package com.zyc.copier_v0.modules.user.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        "spring.datasource.url=jdbc:h2:mem:platformauth;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "copier.account-config.route-cache.backend=log",
        "copier.mt5.signal-ingest.dedup-backend=memory",
        "copier.monitor.runtime-state.backend=database",
        "copier.monitor.session-registry.backend=memory",
        "copier.mt5.follower-exec.realtime-dispatch.backend=local"
})
class PlatformAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRegisterLoginReadCurrentUserAndLogout() throws Exception {
        Map<String, Object> registerPayload = new HashMap<>();
        registerPayload.put("username", "alice");
        registerPayload.put("password", "secret123");
        registerPayload.put("displayName", "Alice");

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformId").isNotEmpty())
                .andExpect(jsonPath("$.shareId").isNotEmpty())
                .andExpect(jsonPath("$.username").value("alice"))
                .andReturn();

        Cookie registerCookie = registerResult.getResponse().getCookie("TEST_SESSION");
        JsonNode registerJson = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String platformId = registerJson.path("platformId").asText();

        mockMvc.perform(get("/api/auth/me").cookie(registerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformId").value(platformId))
                .andExpect(jsonPath("$.displayName").value("Alice"));

        mockMvc.perform(post("/api/auth/logout").cookie(registerCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").cookie(registerCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        Map<String, Object> loginPayload = new HashMap<>();
        loginPayload.put("login", platformId);
        loginPayload.put("password", "secret123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformId").value(platformId))
                .andReturn();

        Cookie loginCookie = loginResult.getResponse().getCookie("TEST_SESSION");
        mockMvc.perform(get("/api/auth/me").cookie(loginCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformId").value(platformId));

        Map<String, Object> updatePayload = new HashMap<>();
        updatePayload.put("displayName", "Alice Ops");
        updatePayload.put("currentPassword", "secret123");
        updatePayload.put("newPassword", "secret456");

        mockMvc.perform(put("/api/auth/me")
                        .cookie(loginCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice Ops"));

        mockMvc.perform(post("/api/auth/logout").cookie(loginCookie))
                .andExpect(status().isOk());

        Map<String, Object> oldPasswordPayload = new HashMap<>();
        oldPasswordPayload.put("login", platformId);
        oldPasswordPayload.put("password", "secret123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldPasswordPayload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        Map<String, Object> newPasswordPayload = new HashMap<>();
        newPasswordPayload.put("login", platformId);
        newPasswordPayload.put("password", "secret456");

        Cookie updatedCookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newPasswordPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice Ops"))
                .andReturn()
                .getResponse()
                .getCookie("TEST_SESSION");

        mockMvc.perform(get("/api/auth/me").cookie(updatedCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice Ops"));
    }
}
