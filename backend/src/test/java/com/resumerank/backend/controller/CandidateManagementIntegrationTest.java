package com.resumerank.backend.controller;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.resumerank.backend.dto.*;
import com.resumerank.backend.entity.*;
import com.resumerank.backend.repository.*;
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
import org.springframework.web.client.RestTemplate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CandidateManagementIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private CandidateScoreRepository candidateScoreRepository;

    @Autowired
    private CandidateStatusLogRepository candidateStatusLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private RestTemplate restTemplate; // Mock AI service HTTP client calls

    private User recruiter;
    private JobPosting jobPosting;
    private String token;
    private String internalWebhookToken = "5ec834ec8d0b81d070cde05c99231c6bab517c11f510cf5139353204841b42b8";

    @BeforeEach
    void setUp() {
        candidateScoreRepository.deleteAll();
        candidateStatusLogRepository.deleteAll();
        candidateRepository.deleteAll();
        jobPostingRepository.deleteAll();
        userRepository.deleteAll();

        // Setup owner recruiter
        recruiter = TestEntityGenerator.createMockUser("recruiter.candidate.test@example.com");
        recruiter = userRepository.saveAndFlush(recruiter);

        // Setup job posting
        jobPosting = TestEntityGenerator.createMockJobPosting("React Front-End Engineer", recruiter);
        jobPosting.setUser(recruiter);
        jobPosting = jobPostingRepository.saveAndFlush(jobPosting);

        token = "valid_candidate_auth_jwt";

        // Mock JWT Interceptor
        DecodedJWT decodedJWT = Mockito.mock(DecodedJWT.class);
        Mockito.when(decodedJWT.getSubject()).thenReturn(recruiter.getId().toString());

        Claim emailClaim = Mockito.mock(Claim.class);
        Mockito.when(emailClaim.asString()).thenReturn(recruiter.getEmail());
        Mockito.when(decodedJWT.getClaim("email")).thenReturn(emailClaim);

        Claim typeClaim = Mockito.mock(Claim.class);
        Mockito.when(typeClaim.asString()).thenReturn("access");
        Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);

        Mockito.when(jwtService.verifyToken(token)).thenReturn(decodedJWT);
    }

    @Test
    void uploadSignature_Authenticated_ReturnsCloudinarySignature() throws Exception {
        mockMvc.perform(post("/api/uploads/signature")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature").exists())
                .andExpect(jsonPath("$.folder").value("resumes/" + recruiter.getId().toString()));
    }

    @Test
    void createCandidate_ValidRequest_SavesCandidateAsPending() throws Exception {
        CandidateCreateRequest request = new CandidateCreateRequest("http://cloudinary.com/resumes/candidate.pdf", "hash-val-abc");

        mockMvc.perform(post("/api/job-postings/" + jobPosting.getId() + "/candidates")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumeStatus").value("PENDING"))
                .andExpect(jsonPath("$.pipelineStatus").value("NEW"))
                .andExpect(jsonPath("$.resumeFileUrl").value("http://cloudinary.com/resumes/candidate.pdf"));

        Assertions.assertEquals(1, candidateRepository.count());
    }

    @Test
    void createCandidate_DuplicateHash_IdentifiesDuplicate() throws Exception {
        // Save candidate 1 with hash
        Candidate c1 = TestEntityGenerator.createMockCandidate(jobPosting, "Alice", "alice@example.com");
        c1.setResumeHash("duplicate-hash-111");
        c1 = candidateRepository.saveAndFlush(c1);

        CandidateCreateRequest request = new CandidateCreateRequest("http://cloudinary.com/resumes/second.pdf", "duplicate-hash-111");

        mockMvc.perform(post("/api/job-postings/" + jobPosting.getId() + "/candidates")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duplicateOfCandidateId").value(c1.getId().toString()));
    }

    @Test
    void aiWebhook_SuccessPayload_TransitionsStatusToScoredAndCreatesScore() throws Exception {
        // Create candidate
        Candidate c = TestEntityGenerator.createMockCandidate(jobPosting, "Bob", "bob@example.com");
        c = candidateRepository.saveAndFlush(c);

        AiWebhookPayload payload = new AiWebhookPayload();
        payload.setCandidateId(c.getId().toString());
        payload.setSuccess(true);

        AiWebhookPayload.ScoreDto score = new AiWebhookPayload.ScoreDto();
        score.setOverallScore(92);
        score.setSkillsScore(95);
        score.setExperienceScore(90);
        score.setSeniorityScore(90);
        score.setMatchedSkills(List.of("React", "TypeScript"));
        score.setMissingSkills(List.of("Redux"));
        score.setSummary("Bob is highly qualified.");
        score.setYearsExperienceDetected(4.5);
        payload.setScore(score);

        mockMvc.perform(post("/api/internal/ai-webhook")
                .header("X-Internal-Token", internalWebhookToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // Verify transition to SCORED in H2
        Candidate updatedCandidate = candidateRepository.findById(c.getId()).orElseThrow();
        Assertions.assertEquals(ResumeStatus.SCORED, updatedCandidate.getResumeStatus());
        Assertions.assertEquals("Bob", updatedCandidate.getName());
        Assertions.assertEquals("bob@example.com", updatedCandidate.getEmail());

        // Verify score records
        CandidateScore candidateScore = candidateScoreRepository.findByCandidateId(c.getId()).orElseThrow();
        Assertions.assertEquals(92, candidateScore.getOverallScore());
        Assertions.assertEquals("Bob is highly qualified.", candidateScore.getSummary());
    }

    @Test
    void aiWebhook_FailPayload_TransitionsStatusToFailedAndSetsParseError() throws Exception {
        Candidate c = TestEntityGenerator.createMockCandidate(jobPosting, "Charlie", "charlie@example.com");
        c = candidateRepository.saveAndFlush(c);

        AiWebhookPayload payload = new AiWebhookPayload();
        payload.setCandidateId(c.getId().toString());
        payload.setSuccess(false);
        payload.setError("Failed to parse corrupted PDF file");

        mockMvc.perform(post("/api/internal/ai-webhook")
                .header("X-Internal-Token", internalWebhookToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        Candidate updatedCandidate = candidateRepository.findById(c.getId()).orElseThrow();
        Assertions.assertEquals(ResumeStatus.FAILED, updatedCandidate.getResumeStatus());
        Assertions.assertEquals("Failed to parse corrupted PDF file", updatedCandidate.getParseError());
    }

    @Test
    void getCandidateDetail_OwnerAuthorized_ReturnsFullDetails() throws Exception {
        Candidate c = TestEntityGenerator.createMockCandidate(jobPosting, "David Miller", "david@example.com");
        c = candidateRepository.saveAndFlush(c);

        mockMvc.perform(get("/api/candidates/" + c.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(c.getId().toString()))
                .andExpect(jsonPath("$.name").value("David Miller"));
    }

    @Test
    void getCandidateDetail_UnauthorizedRecruiter_Returns404() throws Exception {
        // Create Recruiter B
        User recruiterB = TestEntityGenerator.createMockUser("recruiterB@example.com");
        recruiterB = userRepository.saveAndFlush(recruiterB);

        // Mock JWT for Recruiter B
        String tokenB = "valid_candidate_auth_jwt_recruiter_b";
        DecodedJWT decodedJWTB = Mockito.mock(DecodedJWT.class);
        Mockito.when(decodedJWTB.getSubject()).thenReturn(recruiterB.getId().toString());
        Claim emailClaimB = Mockito.mock(Claim.class);
        Mockito.when(emailClaimB.asString()).thenReturn(recruiterB.getEmail());
        Mockito.when(decodedJWTB.getClaim("email")).thenReturn(emailClaimB);
        Claim typeClaimB = Mockito.mock(Claim.class);
        Mockito.when(typeClaimB.asString()).thenReturn("access");
        Mockito.when(decodedJWTB.getClaim("type")).thenReturn(typeClaimB);
        Mockito.when(jwtService.verifyToken(tokenB)).thenReturn(decodedJWTB);

        // Create a candidate for jobPosting (owned by Recruiter A)
        Candidate c = TestEntityGenerator.createMockCandidate(jobPosting, "David Miller", "david@example.com");
        c = candidateRepository.saveAndFlush(c);

        // Fetch candidate detail as recruiterB -> should return 404
        mockMvc.perform(get("/api/candidates/" + c.getId())
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCandidateStatus_UnauthorizedRecruiter_Returns404() throws Exception {
        // Create Recruiter B
        User recruiterB = TestEntityGenerator.createMockUser("recruiterB@example.com");
        recruiterB = userRepository.saveAndFlush(recruiterB);

        // Mock JWT for Recruiter B
        String tokenB = "valid_candidate_auth_jwt_recruiter_b";
        DecodedJWT decodedJWTB = Mockito.mock(DecodedJWT.class);
        Mockito.when(decodedJWTB.getSubject()).thenReturn(recruiterB.getId().toString());
        Claim emailClaimB = Mockito.mock(Claim.class);
        Mockito.when(emailClaimB.asString()).thenReturn(recruiterB.getEmail());
        Mockito.when(decodedJWTB.getClaim("email")).thenReturn(emailClaimB);
        Claim typeClaimB = Mockito.mock(Claim.class);
        Mockito.when(typeClaimB.asString()).thenReturn("access");
        Mockito.when(decodedJWTB.getClaim("type")).thenReturn(typeClaimB);
        Mockito.when(jwtService.verifyToken(tokenB)).thenReturn(decodedJWTB);

        // Create a candidate for jobPosting (owned by Recruiter A)
        Candidate c = TestEntityGenerator.createMockCandidate(jobPosting, "David Miller", "david@example.com");
        c = candidateRepository.saveAndFlush(c);

        CandidateStatusUpdateRequest updateRequest = new CandidateStatusUpdateRequest("SHORTLISTED");

        // Attempt status transition as recruiterB -> should return 404
        mockMvc.perform(patch("/api/candidates/" + c.getId() + "/status")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void candidateListFilteringAndSearch_ReturnsFilteredItems() throws Exception {
        // Alice: Scored, 90
        Candidate alice = TestEntityGenerator.createMockCandidate(jobPosting, "Alice Smith", "alice@example.com");
        alice.setResumeStatus(ResumeStatus.SCORED);
        alice = candidateRepository.saveAndFlush(alice);
        CandidateScore aliceScore = new CandidateScore();
        aliceScore.setCandidate(alice);
        aliceScore.setOverallScore(90);
        aliceScore.setMatchedSkills(new String[]{"React", "Java"});
        aliceScore.setMissingSkills(new String[]{});
        candidateScoreRepository.saveAndFlush(aliceScore);

        // Bob: Scored, 50
        Candidate bob = TestEntityGenerator.createMockCandidate(jobPosting, "Bob Jones", "bob@example.com");
        bob.setResumeStatus(ResumeStatus.SCORED);
        bob = candidateRepository.saveAndFlush(bob);
        CandidateScore bobScore = new CandidateScore();
        bobScore.setCandidate(bob);
        bobScore.setOverallScore(50);
        bobScore.setMatchedSkills(new String[]{"Python"});
        bobScore.setMissingSkills(new String[]{});
        candidateScoreRepository.saveAndFlush(bobScore);

        // 1. Search by name "Bob"
        mockMvc.perform(get("/api/job-postings/" + jobPosting.getId() + "/candidates?search=Bob")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Bob Jones"));

        // 2. Filter by minScore = 80
        mockMvc.perform(get("/api/job-postings/" + jobPosting.getId() + "/candidates?minScore=80")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Alice Smith"));
    }

    @Test
    void candidateSoftDelete_ExcludedFromListings() throws Exception {
        Candidate c = TestEntityGenerator.createMockCandidate(jobPosting, "Delete Target", "delete@example.com");
        c = candidateRepository.saveAndFlush(c);

        // Make sure it appears in normal list first
        mockMvc.perform(get("/api/job-postings/" + jobPosting.getId() + "/candidates")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        // Perform Soft Delete by setting deletedAt
        c.setDeletedAt(OffsetDateTime.now());
        candidateRepository.saveAndFlush(c);

        // Verify it is excluded now
        mockMvc.perform(get("/api/job-postings/" + jobPosting.getId() + "/candidates")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void updateCandidateStatus_ValidTransition_UpdatesPipelineAndWritesLog() throws Exception {
        Candidate c = TestEntityGenerator.createMockCandidate(jobPosting, "Zoe", "zoe@example.com");
        c = candidateRepository.saveAndFlush(c);

        CandidateStatusUpdateRequest updateRequest = new CandidateStatusUpdateRequest("SHORTLISTED");

        mockMvc.perform(patch("/api/candidates/" + c.getId() + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineStatus").value("SHORTLISTED"));

        // Check DB update
        Candidate updated = candidateRepository.findById(c.getId()).orElseThrow();
        Assertions.assertEquals(PipelineStatus.SHORTLISTED, updated.getPipelineStatus());

        // Check transition log
        List<CandidateStatusLog> logs = candidateStatusLogRepository.findAllByCandidateIdOrderByCreatedAtDesc(c.getId());
        Assertions.assertEquals(1, logs.size());
        Assertions.assertEquals("NEW", logs.get(0).getFromStatus());
        Assertions.assertEquals("SHORTLISTED", logs.get(0).getToStatus());
        Assertions.assertEquals(recruiter.getEmail(), logs.get(0).getChangedByEmail());
    }

    @Test
    void bulkStatusUpdate_MultipleCandidates_UpdatesAllMatchingRecruiterOwnership() throws Exception {
        Candidate c1 = TestEntityGenerator.createMockCandidate(jobPosting, "C1", "c1@example.com");
        c1 = candidateRepository.saveAndFlush(c1);

        Candidate c2 = TestEntityGenerator.createMockCandidate(jobPosting, "C2", "c2@example.com");
        c2 = candidateRepository.saveAndFlush(c2);

        BulkStatusUpdateRequest bulkRequest = new BulkStatusUpdateRequest(
                List.of(c1.getId().toString(), c2.getId().toString()),
                "REVIEWING"
        );

        mockMvc.perform(post("/api/candidates/bulk-status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bulkRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated.length()").value(2))
                .andExpect(jsonPath("$.updated[0]").value(c1.getId().toString()));

        Assertions.assertEquals(PipelineStatus.REVIEWING, candidateRepository.findById(c1.getId()).orElseThrow().getPipelineStatus());
        Assertions.assertEquals(PipelineStatus.REVIEWING, candidateRepository.findById(c2.getId()).orElseThrow().getPipelineStatus());
    }
}
