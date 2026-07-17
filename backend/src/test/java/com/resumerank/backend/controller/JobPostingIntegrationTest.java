package com.resumerank.backend.controller;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.resumerank.backend.dto.JobPostingCreateRequest;
import com.resumerank.backend.dto.JobPostingResponse;
import com.resumerank.backend.entity.JobPosting;
import com.resumerank.backend.entity.JobPostingStatus;
import com.resumerank.backend.entity.SeniorityLevel;
import com.resumerank.backend.entity.User;
import com.resumerank.backend.repository.JobPostingRepository;
import com.resumerank.backend.repository.UserRepository;
import com.resumerank.backend.service.JwtService;
import com.resumerank.backend.support.BaseIntegrationTest;
import com.resumerank.backend.support.TestEntityGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobPostingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtService jwtService;

    private User owner;
    private String token;

    @BeforeEach
    void setUp() {
        // Clear H2 repositories
        jobPostingRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user in DB
        owner = TestEntityGenerator.createMockUser("recruiter.owner@example.com");
        owner = userRepository.saveAndFlush(owner);

        token = "valid_jwt_token_123";

        // Mock JWT Interceptor check to return the created user's ID
        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        Mockito.when(decodedJWT.getSubject()).thenReturn(owner.getId().toString());

        Claim emailClaim = Mockito.mock(Claim.class);
        Mockito.when(emailClaim.asString()).thenReturn(owner.getEmail());
        Mockito.when(decodedJWT.getClaim("email")).thenReturn(emailClaim);

        Claim typeClaim = Mockito.mock(Claim.class);
        Mockito.when(typeClaim.asString()).thenReturn("access");
        Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);

        Mockito.when(jwtService.verifyToken(token)).thenReturn(decodedJWT);
    }

    @Test
    void createJobPosting_ValidRequest_SavesToH2AndReturns201() throws Exception {
        JobPostingCreateRequest request = new JobPostingCreateRequest(
                "Software Architect",
                "Mock job description",
                new String[]{"Java", "AWS"},
                new String[]{"Terraform"},
                8,
                SeniorityLevel.LEAD
        );

        mockMvc.perform(post("/api/job-postings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Software Architect"))
                .andExpect(jsonPath("$.userId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify it was actually saved in H2 Database
        Assertions.assertEquals(1, jobPostingRepository.count());
        JobPosting savedJob = jobPostingRepository.findAll().get(0);
        Assertions.assertEquals("Software Architect", savedJob.getTitle());
        Assertions.assertEquals(owner.getId(), savedJob.getUser().getId());
    }

    @Test
    void getJobPosting_ExistingOwnJob_Returns200() throws Exception {
        JobPosting job = TestEntityGenerator.createMockJobPosting("DevOps Specialist", owner);
        job.setUser(owner);
        job = jobPostingRepository.saveAndFlush(job);

        mockMvc.perform(get("/api/job-postings/" + job.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(job.getId().toString()))
                .andExpect(jsonPath("$.title").value("DevOps Specialist"));
    }

    @Test
    void getJobPosting_NonexistentJob_Returns404() throws Exception {
        UUID nonexistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/job-postings/" + nonexistentId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchJobPosting_UpdatesFieldsSuccessfully_Returns200() throws Exception {
        JobPosting job = TestEntityGenerator.createMockJobPosting("Backend Dev", owner);
        job.setUser(owner);
        job = jobPostingRepository.saveAndFlush(job);

        String patchJson = "{\"title\":\"Senior Backend Dev\",\"status\":\"archived\"}";

        mockMvc.perform(patch("/api/job-postings/" + job.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Senior Backend Dev"))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // Verify changes are persistent in H2
        JobPosting updatedJob = jobPostingRepository.findById(job.getId()).orElseThrow();
        Assertions.assertEquals("Senior Backend Dev", updatedJob.getTitle());
        Assertions.assertEquals(JobPostingStatus.ARCHIVED, updatedJob.getStatus());
    }

    @Test
    void patchJobPosting_NotOwnJob_Returns404() throws Exception {
        // Create another user to own the posting
        User otherUser = TestEntityGenerator.createMockUser("other.recruiter@example.com");
        otherUser = userRepository.saveAndFlush(otherUser);

        JobPosting job = TestEntityGenerator.createMockJobPosting("Frontend Dev", otherUser);
        job.setUser(otherUser);
        job = jobPostingRepository.saveAndFlush(job);

        String patchJson = "{\"title\":\"Hacked Title\"}";

        mockMvc.perform(patch("/api/job-postings/" + job.getId())
                .header("Authorization", "Bearer " + token) // Owner token (not otherUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchJson))
                .andExpect(status().isNotFound());

        // Verify repository was not updated
        JobPosting notUpdatedJob = jobPostingRepository.findById(job.getId()).orElseThrow();
        Assertions.assertEquals("Frontend Dev", notUpdatedJob.getTitle());
    }

    @Test
    void listJobPostings_ReturnsPagedListForAuthenticatedOwner() throws Exception {
        JobPosting job1 = TestEntityGenerator.createMockJobPosting("Job 1", owner);
        job1.setUser(owner);
        jobPostingRepository.saveAndFlush(job1);

        JobPosting job2 = TestEntityGenerator.createMockJobPosting("Job 2", owner);
        job2.setUser(owner);
        jobPostingRepository.saveAndFlush(job2);

        mockMvc.perform(get("/api/job-postings?page=0&size=10")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalItems").value(2));
    }
}
