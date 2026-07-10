package com.resumerank.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerank.backend.controller.AuthController;
import com.resumerank.backend.dto.LoginRequest;
import com.resumerank.backend.dto.LoginResponse;
import com.resumerank.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(RateLimitConfig.class)
class RateLimiterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void rateLimit_KeysByEmailAndIp() throws Exception {
        LoginRequest request1 = new LoginRequest("user1@example.com", "password123");
        LoginRequest request2 = new LoginRequest("user2@example.com", "password123");
        LoginResponse dummyResponse = new LoginResponse("access", "refresh", true);

        Mockito.when(authService.login(any(LoginRequest.class))).thenReturn(dummyResponse);

        String clientIp = "192.168.1.50";

        // 1. Perform 5 rapid requests with user1 -> should succeed (200)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .header("X-Forwarded-For", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request1)))
                    .andExpect(status().isOk());
        }

        // 2. 6th request with user1 from same IP -> should be blocked (429)
        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // 3. Request with user2 from same IP -> should succeed (200), proving it's not IP-only
        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk());
    }
}
