package com.resumerank.backend.controller;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerank.backend.config.JwtInterceptor;
import com.resumerank.backend.dto.JobPostingCreateRequest;
import com.resumerank.backend.dto.JobPostingResponse;
import com.resumerank.backend.entity.JobPostingStatus;
import com.resumerank.backend.entity.SeniorityLevel;
import com.resumerank.backend.service.JobPostingService;
import com.resumerank.backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobPostingController.class)
@Import(JwtInterceptor.class)
class JobPostingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobPostingService jobPostingService;

    @MockBean
    private JwtService jwtService;

    private UUID mockUserId;
    private String mockToken;

    @BeforeEach
    void setUp() {
        mockUserId = UUID.randomUUID();
        mockToken = "validAccessToken123";
    }

    @Test
    void createJobPosting_Authenticated_Returns201() throws Exception {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "Software Engineer",
                "Job description here",
                new String[]{"Java"},
                new String[]{"SQL"},
                3,
                SeniorityLevel.MID
        );

        JobPostingResponse response = new JobPostingResponse(
                UUID.randomUUID(),
                mockUserId,
                "Software Engineer",
                "Job description here",
                new String[]{"Java"},
                new String[]{"SQL"},
                3,
                SeniorityLevel.MID,
                JobPostingStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        Mockito.when(decodedJWT.getSubject()).thenReturn(mockUserId.toString());
        
        Claim emailClaim = Mockito.mock(Claim.class);
        Mockito.when(emailClaim.asString()).thenReturn("recruiter@example.com");
        Mockito.when(decodedJWT.getClaim("email")).thenReturn(emailClaim);

        Claim typeClaim = Mockito.mock(Claim.class);
        Mockito.when(typeClaim.asString()).thenReturn("access");
        Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);

        Mockito.when(jwtService.verifyToken(mockToken)).thenReturn(decodedJWT);

        Mockito.when(jobPostingService.createJobPosting(eq(mockUserId), any(JobPostingCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/job-postings")
                .header("Authorization", "Bearer " + mockToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(mockUserId.toString()))
                .andExpect(jsonPath("$.title").value("Software Engineer"));
    }

    @Test
    void createJobPosting_MissingTitle_Returns400() throws Exception {
        String requestJson = "{\"description\":\"Just a description\"}";

        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        Mockito.when(decodedJWT.getSubject()).thenReturn(mockUserId.toString());
        
        Claim emailClaim = Mockito.mock(Claim.class);
        Mockito.when(emailClaim.asString()).thenReturn("recruiter@example.com");
        Mockito.when(decodedJWT.getClaim("email")).thenReturn(emailClaim);

        Claim typeClaim = Mockito.mock(Claim.class);
        Mockito.when(typeClaim.asString()).thenReturn("access");
        Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);

        Mockito.when(jwtService.verifyToken(mockToken)).thenReturn(decodedJWT);

        mockMvc.perform(post("/api/job-postings")
                .header("Authorization", "Bearer " + mockToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createJobPosting_NoToken_Returns401() throws Exception {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "Software Engineer",
                "Job description here",
                null, null, null, null
        );

        mockMvc.perform(post("/api/job-postings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createJobPosting_WithDifferentUserIdInBody_StillUsesAuthenticatedUserId() throws Exception {
        UUID attackerUserId = UUID.randomUUID();
        String requestJson = "{"
                + "\"title\":\"Software Engineer\","
                + "\"description\":\"Job description here\","
                + "\"userId\":\"" + attackerUserId.toString() + "\""
                + "}";

        JobPostingResponse response = new JobPostingResponse(
                UUID.randomUUID(),
                mockUserId, // Owner must be the authenticated user, NOT the attacker!
                "Software Engineer",
                "Job description here",
                new String[0],
                new String[0],
                null,
                null,
                JobPostingStatus.ACTIVE,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        Mockito.when(decodedJWT.getSubject()).thenReturn(mockUserId.toString());
        
        Claim emailClaim = Mockito.mock(Claim.class);
        Mockito.when(emailClaim.asString()).thenReturn("recruiter@example.com");
        Mockito.when(decodedJWT.getClaim("email")).thenReturn(emailClaim);

        Claim typeClaim = Mockito.mock(Claim.class);
        Mockito.when(typeClaim.asString()).thenReturn("access");
        Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);

        Mockito.when(jwtService.verifyToken(mockToken)).thenReturn(decodedJWT);

        Mockito.when(jobPostingService.createJobPosting(eq(mockUserId), any(JobPostingCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/job-postings")
                .header("Authorization", "Bearer " + mockToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(mockUserId.toString()))
                .andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.not(attackerUserId.toString())));
    }
}
