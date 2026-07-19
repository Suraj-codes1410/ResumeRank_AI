package com.resumerank.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerank.backend.config.JwtInterceptor;
import com.resumerank.backend.dto.AiWebhookPayload;
import com.resumerank.backend.service.CandidateService;
import com.resumerank.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@WebMvcTest(InternalWebhookController.class)
@Import(JwtInterceptor.class)
@TestPropertySource(properties = "INTERNAL_SERVICE_TOKEN=test-token-123")
@ActiveProfiles("test")
class InternalWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CandidateService candidateService;

    @MockBean
    private JwtService jwtService;

    private String correctToken = "test-token-123";

    @Test
    void receiveWebhook_NoToken_Returns401() throws Exception {
        AiWebhookPayload payload = new AiWebhookPayload();
        payload.setCandidateId("48bb82d2-8b6d-4ee8-b80c-a1d2e74aaea9");
        payload.setSuccess(false);
        payload.setError("Failed");

        mockMvc.perform(post("/api/internal/ai-webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receiveWebhook_WrongToken_Returns401() throws Exception {
        AiWebhookPayload payload = new AiWebhookPayload();
        payload.setCandidateId("48bb82d2-8b6d-4ee8-b80c-a1d2e74aaea9");
        payload.setSuccess(false);

        mockMvc.perform(post("/api/internal/ai-webhook")
                        .header("X-Internal-Token", "wrong-token-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receiveWebhook_CorrectToken_DelegatesToServiceAndReturns200() throws Exception {
        AiWebhookPayload payload = new AiWebhookPayload();
        payload.setCandidateId("48bb82d2-8b6d-4ee8-b80c-a1d2e74aaea9");
        payload.setSuccess(false);
        payload.setError("AI processing failed");

        Mockito.doNothing().when(candidateService).handleAiWebhook(any(AiWebhookPayload.class));

        mockMvc.perform(post("/api/internal/ai-webhook")
                        .header("X-Internal-Token", correctToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        Mockito.verify(candidateService).handleAiWebhook(any(AiWebhookPayload.class));
    }
}
