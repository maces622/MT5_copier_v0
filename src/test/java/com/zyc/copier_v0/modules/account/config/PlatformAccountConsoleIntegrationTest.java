package com.zyc.copier_v0.modules.account.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyc.copier_v0.modules.account.config.domain.CopyMode;
import com.zyc.copier_v0.modules.copy.engine.domain.ExecutionCommandStatus;
import com.zyc.copier_v0.modules.copy.engine.domain.ExecutionCommandType;
import com.zyc.copier_v0.modules.copy.engine.domain.FollowerDispatchStatus;
import com.zyc.copier_v0.modules.copy.engine.entity.ExecutionCommandEntity;
import com.zyc.copier_v0.modules.copy.engine.entity.FollowerDispatchOutboxEntity;
import com.zyc.copier_v0.modules.copy.engine.repository.ExecutionCommandRepository;
import com.zyc.copier_v0.modules.copy.engine.repository.FollowerDispatchOutboxRepository;
import com.zyc.copier_v0.modules.signal.ingest.domain.Mt5SignalType;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:platformconsole;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "copier.account-config.route-cache.backend=log",
        "copier.mt5.signal-ingest.dedup-backend=memory",
        "copier.monitor.runtime-state.backend=database",
        "copier.monitor.session-registry.backend=memory",
        "copier.mt5.follower-exec.realtime-dispatch.backend=local"
})
class PlatformAccountConsoleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutionCommandRepository executionCommandRepository;

    @Autowired
    private FollowerDispatchOutboxRepository followerDispatchOutboxRepository;

    @Test
    void shouldExposeMyAccountsAccountDetailAndMonitorViews() throws Exception {
        AuthFixture user = registerUser("console-owner");
        Long masterAccountId = bindAccount(user.userId(), "MASTER");
        Long followerAccountId = bindAccount(user.userId(), "FOLLOWER");

        Map<String, Object> riskPayload = new HashMap<>();
        riskPayload.put("accountId", followerAccountId);
        riskPayload.put("balanceRatio", 1.0);
        riskPayload.put("maxLot", 1.0);
        mockMvc.perform(post("/api/risk-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(riskPayload)))
                .andExpect(status().isOk());

        Map<String, Object> relationPayload = new HashMap<>();
        relationPayload.put("masterAccountId", masterAccountId);
        relationPayload.put("followerAccountId", followerAccountId);
        relationPayload.put("copyMode", "BALANCE_RATIO");
        relationPayload.put("status", "ACTIVE");
        mockMvc.perform(post("/api/copy-relations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(relationPayload)))
                .andExpect(status().isOk());

        ExecutionCommandEntity command = new ExecutionCommandEntity();
        command.setMasterEventId("MASTER-EVENT-1");
        command.setMasterAccountId(masterAccountId);
        command.setMasterAccountKey("Server-MASTER:" + masterAccountId);
        command.setFollowerAccountId(followerAccountId);
        command.setMasterSymbol("XAUUSD");
        command.setSignalType(Mt5SignalType.DEAL);
        command.setCommandType(ExecutionCommandType.OPEN_POSITION);
        command.setSymbol("XAUUSDm");
        command.setMasterAction("BUY OPEN");
        command.setFollowerAction("BUY OPEN");
        command.setCopyMode(CopyMode.BALANCE_RATIO);
        command.setMasterOrderId(880001L);
        command.setMasterPositionId(990001L);
        command.setStatus(ExecutionCommandStatus.READY);
        command.setSignalTime("2026.03.22 10:00:00");
        ExecutionCommandEntity savedCommand = executionCommandRepository.save(command);

        FollowerDispatchOutboxEntity dispatch = new FollowerDispatchOutboxEntity();
        dispatch.setExecutionCommandId(savedCommand.getId());
        dispatch.setMasterEventId(savedCommand.getMasterEventId());
        dispatch.setFollowerAccountId(followerAccountId);
        dispatch.setStatus(FollowerDispatchStatus.PENDING);
        dispatch.setPayloadJson("{\"commandId\":" + savedCommand.getId() + "}");
        followerDispatchOutboxRepository.save(dispatch);

        mockMvc.perform(get("/api/me/accounts").cookie(user.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/me/copy-relations").cookie(user.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].relation.masterAccountId").value(masterAccountId))
                .andExpect(jsonPath("$[0].relation.followerAccountId").value(followerAccountId))
                .andExpect(jsonPath("$[0].currentUserOwnsMaster").value(true))
                .andExpect(jsonPath("$[0].currentUserOwnsFollower").value(true));

        mockMvc.perform(get("/api/accounts/{accountId}/detail", followerAccountId).cookie(user.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.id").value(followerAccountId))
                .andExpect(jsonPath("$.riskRule.accountId").value(followerAccountId))
                .andExpect(jsonPath("$.relations.followerRelations[0].masterAccountId").value(masterAccountId));

        mockMvc.perform(get("/api/monitor/dashboard").cookie(user.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAccounts").value(2))
                .andExpect(jsonPath("$.pendingDispatchCount").value(1));

        mockMvc.perform(get("/api/monitor/accounts/{accountId}/detail", followerAccountId).cookie(user.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.accountId").value(followerAccountId));

        mockMvc.perform(get("/api/monitor/accounts/{accountId}/commands", followerAccountId).cookie(user.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(savedCommand.getId()))
                .andExpect(jsonPath("$[0].commandType").value("OPEN_POSITION"));

        mockMvc.perform(get("/api/monitor/accounts/{accountId}/dispatches", masterAccountId).cookie(user.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].executionCommandId").value(savedCommand.getId()))
                .andExpect(jsonPath("$[0].followerAccountId").value(followerAccountId));

        mockMvc.perform(get("/api/monitor/traces/order")
                        .cookie(user.cookie())
                        .param("masterAccountId", String.valueOf(masterAccountId))
                        .param("masterOrderId", "880001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masterAccountId").value(masterAccountId))
                .andExpect(jsonPath("$.masterOrderId").value(880001))
                .andExpect(jsonPath("$.commands[0].id").value(savedCommand.getId()));

        mockMvc.perform(get("/api/monitor/traces/position")
                        .cookie(user.cookie())
                        .param("masterAccountId", String.valueOf(masterAccountId))
                        .param("masterPositionId", "990001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masterAccountId").value(masterAccountId))
                .andExpect(jsonPath("$.masterPositionId").value(990001))
                .andExpect(jsonPath("$.dispatches[0].executionCommandId").value(savedCommand.getId()));
    }

    @Test
    void shouldAllowCurrentUserMutationEndpointsWithoutPassingUserId() throws Exception {
        AuthFixture user = registerUser("console-mutate");

        Map<String, Object> bindPayload = new HashMap<>();
        bindPayload.put("brokerName", "Broker-MASTER");
        bindPayload.put("serverName", "Server-MASTER-" + System.nanoTime());
        bindPayload.put("mt5Login", System.nanoTime());
        bindPayload.put("accountRole", "MASTER");
        bindPayload.put("status", "ACTIVE");

        MvcResult masterResult = mockMvc.perform(post("/api/me/accounts")
                        .cookie(user.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bindPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.userId()))
                .andReturn();

        Long masterAccountId = objectMapper.readTree(masterResult.getResponse().getContentAsString()).path("id").asLong();

        bindPayload.put("brokerName", "Broker-FOLLOWER");
        bindPayload.put("serverName", "Server-FOLLOWER-" + System.nanoTime());
        bindPayload.put("mt5Login", System.nanoTime());
        bindPayload.put("accountRole", "FOLLOWER");

        MvcResult followerResult = mockMvc.perform(post("/api/me/accounts")
                        .cookie(user.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bindPayload)))
                .andExpect(status().isOk())
                .andReturn();

        Long followerAccountId = objectMapper.readTree(followerResult.getResponse().getContentAsString()).path("id").asLong();

        Map<String, Object> riskPayload = new HashMap<>();
        riskPayload.put("balanceRatio", 1.2);
        riskPayload.put("maxLot", 2.0);
        riskPayload.put("followTpSl", true);

        mockMvc.perform(post("/api/me/accounts/{accountId}/risk-rule", followerAccountId)
                        .cookie(user.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(riskPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(followerAccountId))
                .andExpect(jsonPath("$.balanceRatio").value(1.2));

        Map<String, Object> relationPayload = new HashMap<>();
        relationPayload.put("masterAccountId", masterAccountId);
        relationPayload.put("followerAccountId", followerAccountId);
        relationPayload.put("copyMode", "BALANCE_RATIO");
        relationPayload.put("status", "ACTIVE");
        relationPayload.put("priority", 120);

        MvcResult relationResult = mockMvc.perform(post("/api/me/copy-relations")
                        .cookie(user.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(relationPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masterAccountId").value(masterAccountId))
                .andExpect(jsonPath("$.followerAccountId").value(followerAccountId))
                .andReturn();

        long relationId = objectMapper.readTree(relationResult.getResponse().getContentAsString()).path("id").asLong();

        Map<String, Object> updatePayload = new HashMap<>();
        updatePayload.put("status", "PAUSED");
        updatePayload.put("priority", 80);

        mockMvc.perform(put("/api/me/copy-relations/{relationId}", relationId)
                        .cookie(user.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.priority").value(80));

        Map<String, Object> mappingPayload = new HashMap<>();
        mappingPayload.put("masterSymbol", "XAUUSD");
        mappingPayload.put("followerSymbol", "XAUUSDm");

        mockMvc.perform(post("/api/me/accounts/{accountId}/symbol-mappings", followerAccountId)
                        .cookie(user.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mappingPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerAccountId").value(followerAccountId))
                .andExpect(jsonPath("$.masterSymbol").value("XAUUSD"));
    }

    private AuthFixture registerUser(String username) throws Exception {
        Map<String, Object> registerPayload = new HashMap<>();
        registerPayload.put("username", username);
        registerPayload.put("password", "secret123");
        registerPayload.put("displayName", username);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerPayload)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie("TEST_SESSION");
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthFixture(json.path("id").asLong(), cookie);
    }

    private Long bindAccount(Long userId, String role) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("brokerName", "Broker-" + role);
        payload.put("serverName", "Server-" + role + "-" + System.nanoTime());
        payload.put("mt5Login", System.nanoTime());
        payload.put("accountRole", role);
        payload.put("status", "ACTIVE");

        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private record AuthFixture(Long userId, Cookie cookie) {
    }
}
