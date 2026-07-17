package com.resumerank.backend.controller;

import com.resumerank.backend.dto.CandidateResponse;
import com.resumerank.backend.dto.CandidateStatusUpdateRequest;
import com.resumerank.backend.dto.CandidateListResponse;
import com.resumerank.backend.entity.*;
import com.resumerank.backend.repository.*;
import com.resumerank.backend.service.JwtService;
import com.resumerank.backend.support.BaseIntegrationTest;
import com.resumerank.backend.support.TestEntityGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CandidateSecurityEndToEndTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService; // Real, unmocked JwtService

    @MockBean
    private RestTemplate restTemplate; // Mock external AI service calls

    private User recruiterA;
    private User recruiterB;
    
    private JobPosting jobPostingA; // Owned by Recruiter A
    private Candidate candidateA;   // Created for Job A (owned by Recruiter A)

    private String tokenA; // Real signed JWT for Recruiter A
    private String tokenB; // Real signed JWT for Recruiter B

    @BeforeEach
    void setUp() {
        candidateRepository.deleteAll();
        jobPostingRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create two distinct user records in database
        recruiterA = TestEntityGenerator.createMockUser("recruiter.a@example.com");
        recruiterA = userRepository.saveAndFlush(recruiterA);

        recruiterB = TestEntityGenerator.createMockUser("recruiter.b@example.com");
        recruiterB = userRepository.saveAndFlush(recruiterB);

        // 2. Obtain real, separately-signed HS256 JWT tokens via real JwtService
        tokenA = jwtService.generateAccessToken(recruiterA.getId(), recruiterA.getEmail());
        tokenB = jwtService.generateAccessToken(recruiterB.getId(), recruiterB.getEmail());

        // 3. Setup a job posting owned by Recruiter A
        jobPostingA = TestEntityGenerator.createMockJobPosting("Java Engineer", recruiterA);
        jobPostingA.setUser(recruiterA);
        jobPostingA = jobPostingRepository.saveAndFlush(jobPostingA);

        // 4. Create a candidate under Recruiter A's job posting
        candidateA = TestEntityGenerator.createMockCandidate(jobPostingA, "John Doe", "john.doe@example.com");
        candidateA = candidateRepository.saveAndFlush(candidateA);
    }

    @Test
    void getCandidateDetail_RealTokenFlow_RecruiterAOwnsAndSucceeds() throws Exception {
        // Recruiter A requests their own candidate with their real token -> should succeed (200 OK)
        mockMvc.perform(get("/api/candidates/" + candidateA.getId())
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getCandidateDetail_RealTokenFlow_RecruiterBUnaffiliatedReturns404() throws Exception {
        // Recruiter B attempts to GET Recruiter A's candidate with their real token -> should fail with 404
        mockMvc.perform(get("/api/candidates/" + candidateA.getId())
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCandidateStatus_RealTokenFlow_RecruiterBUnaffiliatedReturns404() throws Exception {
        CandidateStatusUpdateRequest updateRequest = new CandidateStatusUpdateRequest("SHORTLISTED");

        // Recruiter B attempts to PATCH status of Recruiter A's candidate with their real token -> should fail with 404
        mockMvc.perform(patch("/api/candidates/" + candidateA.getId() + "/status")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        // Verify status remains unchanged in H2 DB
        Candidate current = candidateRepository.findById(candidateA.getId()).orElseThrow();
        Assertions.assertEquals(PipelineStatus.NEW, current.getPipelineStatus());
    }

    @Test
    void getCandidateDetail_InvalidTokenSignature_Returns401() throws Exception {
        // Construct a token with a different signature
        JwtService differentSecretService = new JwtService("different-secret-different-secret-different-secret");
        String badToken = differentSecretService.generateAccessToken(recruiterA.getId(), recruiterA.getEmail());

        // Request with bad signature -> should be blocked by interceptor with 401 Unauthorized
        mockMvc.perform(get("/api/candidates/" + candidateA.getId())
                .header("Authorization", "Bearer " + badToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
