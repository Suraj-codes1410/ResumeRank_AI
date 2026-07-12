package com.resumerank.backend.controller;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerank.backend.config.JwtInterceptor;
import com.resumerank.backend.dto.CandidateCreateRequest;
import com.resumerank.backend.dto.CandidateResponse;
import com.resumerank.backend.entity.PipelineStatus;
import com.resumerank.backend.entity.ResumeStatus;
import com.resumerank.backend.service.CandidateService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CandidateController.class)
@Import(JwtInterceptor.class)
class CandidateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CandidateService candidateService;

    @MockBean
    private JwtService jwtService;

    private UUID mockUserId;
    private UUID mockJobPostingId;
    private String mockToken;

    @BeforeEach
    void setUp() {
        mockUserId = UUID.randomUUID();
        mockJobPostingId = UUID.randomUUID();
        mockToken = "validAccessToken123";
    }

    private void stubJwtVerification() {
        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        Mockito.when(decodedJWT.getSubject()).thenReturn(mockUserId.toString());
        
        Claim emailClaim = Mockito.mock(Claim.class);
        Mockito.when(emailClaim.asString()).thenReturn("recruiter@example.com");
        Mockito.when(decodedJWT.getClaim("email")).thenReturn(emailClaim);

        Claim typeClaim = Mockito.mock(Claim.class);
        Mockito.when(typeClaim.asString()).thenReturn("access");
        Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);

        Mockito.when(jwtService.verifyToken(mockToken)).thenReturn(decodedJWT);
    }

    @Test
    void createCandidate_Authenticated_Returns201() throws Exception {
        stubJwtVerification();

        CandidateCreateRequest request = new CandidateCreateRequest("http://cloudinary.com/resumes/123.pdf");
        CandidateResponse response = new CandidateResponse(
                UUID.randomUUID(),
                mockJobPostingId,
                null,
                null,
                "http://cloudinary.com/resumes/123.pdf",
                ResumeStatus.PENDING,
                null,
                PipelineStatus.NEW,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        Mockito.when(candidateService.createCandidate(eq(mockUserId), eq(mockJobPostingId), any(CandidateCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/job-postings/" + mockJobPostingId + "/candidates")
                        .header("Authorization", "Bearer " + mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobPostingId").value(mockJobPostingId.toString()))
                .andExpect(jsonPath("$.resumeFileUrl").value("http://cloudinary.com/resumes/123.pdf"))
                .andExpect(jsonPath("$.resumeStatus").value("PENDING"));
    }

    @Test
    void createCandidate_EmptyResumeFileUrl_Returns400() throws Exception {
        stubJwtVerification();

        CandidateCreateRequest request = new CandidateCreateRequest(""); // Empty URL

        mockMvc.perform(post("/api/job-postings/" + mockJobPostingId + "/candidates")
                        .header("Authorization", "Bearer " + mockToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCandidates_Authenticated_ReturnsList200() throws Exception {
        stubJwtVerification();

        CandidateResponse response = new CandidateResponse(
                UUID.randomUUID(),
                mockJobPostingId,
                "John Doe",
                "john@example.com",
                "http://cloudinary.com/resumes/123.pdf",
                ResumeStatus.SCORED,
                null,
                PipelineStatus.NEW,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                85
        );

        Mockito.when(candidateService.getCandidatesForJobPosting(eq(mockUserId), eq(mockJobPostingId)))
                .thenReturn(java.util.List.of(response));

        mockMvc.perform(get("/api/job-postings/" + mockJobPostingId + "/candidates")
                        .header("Authorization", "Bearer " + mockToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobPostingId").value(mockJobPostingId.toString()))
                .andExpect(jsonPath("$[0].name").value("John Doe"))
                .andExpect(jsonPath("$[0].overallScore").value(85))
                .andExpect(jsonPath("$[0].resumeStatus").value("SCORED"));
    }

    @Test
    void getCandidates_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/job-postings/" + mockJobPostingId + "/candidates"))
                .andExpect(status().isUnauthorized());
    }
}
