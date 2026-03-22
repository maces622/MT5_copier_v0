package com.zyc.copier_v0.modules.account.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        "spring.datasource.url=jdbc:h2:mem:accountshare;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "copier.account-config.route-cache.backend=log",
        "copier.mt5.signal-ingest.dedup-backend=memory",
        "copier.monitor.runtime-state.backend=database",
        "copier.monitor.session-registry.backend=memory",
        "copier.mt5.follower-exec.realtime-dispatch.backend=local"
})
class AccountShareIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreatePausedCrossUserRelationByShareIdAndShareCode() throws Exception {
        AuthFixture masterUser = registerUser("master-owner");
        AuthFixture followerUser = registerUser("follower-owner");

        Long masterAccountId = bindAccount(masterUser.userId(), "MASTER");
        Long followerAccountId = bindAccount(followerUser.userId(), "FOLLOWER");

        Map<String, Object> sharePayload = new HashMap<>();
        sharePayload.put("shareEnabled", true);
        sharePayload.put("shareCode", "master-code-001");
        sharePayload.put("shareNote", "Main master");

        mockMvc.perform(post("/api/accounts/{accountId}/share-config", masterAccountId)
                        .cookie(masterUser.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sharePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masterAccountId").value(masterAccountId))
                .andExpect(jsonPath("$.shareEnabled").value(true))
                .andExpect(jsonPath("$.shareCodeConfigured").value(true));

        mockMvc.perform(get("/api/me/share-profile").cookie(masterUser.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareId").value(masterUser.shareId()))
                .andExpect(jsonPath("$.masterAccounts[0].masterAccountId").value(masterAccountId))
                .andExpect(jsonPath("$.masterAccounts[0].shareEnabled").value(true));

        Map<String, Object> joinPayload = new HashMap<>();
        joinPayload.put("followerAccountId", followerAccountId);
        joinPayload.put("shareId", masterUser.shareId());
        joinPayload.put("shareCode", "master-code-001");

        MvcResult joinResult = mockMvc.perform(post("/api/copy-relations/join-by-share")
                        .cookie(followerUser.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masterAccountId").value(masterAccountId))
                .andExpect(jsonPath("$.followerAccountId").value(followerAccountId))
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.copyMode").value("BALANCE_RATIO"))
                .andReturn();

        long relationId = objectMapper.readTree(joinResult.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(get("/api/me/copy-relations").cookie(followerUser.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].relation.masterAccountId").value(masterAccountId))
                .andExpect(jsonPath("$[0].relation.followerAccountId").value(followerAccountId))
                .andExpect(jsonPath("$[0].currentUserOwnsMaster").value(false))
                .andExpect(jsonPath("$[0].currentUserOwnsFollower").value(true));

        Map<String, Object> activatePayload = new HashMap<>();
        activatePayload.put("status", "ACTIVE");

        mockMvc.perform(put("/api/me/copy-relations/{relationId}", relationId)
                        .cookie(followerUser.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldKeepExistingFollowRelationAfterMasterRotatesShareCodeAndAllowFollowerUnlink() throws Exception {
        AuthFixture masterUser = registerUser("master-rotate");
        AuthFixture followerUser = registerUser("follower-unlink");

        Long masterAccountId = bindAccount(masterUser.userId(), "MASTER");
        Long followerAccountId = bindAccount(followerUser.userId(), "FOLLOWER");

        Map<String, Object> sharePayload = new HashMap<>();
        sharePayload.put("shareEnabled", true);
        sharePayload.put("shareCode", "first-code");

        mockMvc.perform(post("/api/accounts/{accountId}/share-config", masterAccountId)
                        .cookie(masterUser.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sharePayload)))
                .andExpect(status().isOk());

        Map<String, Object> joinPayload = new HashMap<>();
        joinPayload.put("followerAccountId", followerAccountId);
        joinPayload.put("shareId", masterUser.shareId());
        joinPayload.put("shareCode", "first-code");

        MvcResult joinResult = mockMvc.perform(post("/api/copy-relations/join-by-share")
                        .cookie(followerUser.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinPayload)))
                .andExpect(status().isOk())
                .andReturn();

        long relationId = objectMapper.readTree(joinResult.getResponse().getContentAsString()).path("id").asLong();

        Map<String, Object> rotatePayload = new HashMap<>();
        rotatePayload.put("shareEnabled", true);
        rotatePayload.put("shareCode", "second-code");

        mockMvc.perform(put("/api/accounts/{accountId}/share-config", masterAccountId)
                        .cookie(masterUser.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rotatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareCodeConfigured").value(true));

        mockMvc.perform(get("/api/me/copy-relations").cookie(followerUser.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].relation.id").value(relationId))
                .andExpect(jsonPath("$[0].relation.masterAccountId").value(masterAccountId))
                .andExpect(jsonPath("$[0].relation.followerAccountId").value(followerAccountId));

        mockMvc.perform(delete("/api/me/copy-relations/{relationId}", relationId)
                        .cookie(followerUser.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/copy-relations").cookie(followerUser.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldRequireLoginForShareManagement() throws Exception {
        AuthFixture masterUser = registerUser("share-owner");
        Long masterAccountId = bindAccount(masterUser.userId(), "MASTER");

        Map<String, Object> sharePayload = new HashMap<>();
        sharePayload.put("shareEnabled", true);
        sharePayload.put("shareCode", "secret-code");

        mockMvc.perform(post("/api/accounts/{accountId}/share-config", masterAccountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sharePayload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
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
        return new AuthFixture(
                json.path("id").asLong(),
                json.path("platformId").asText(),
                json.path("shareId").asText(),
                cookie
        );
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

    private record AuthFixture(Long userId, String platformId, String shareId, Cookie cookie) {
    }
}
