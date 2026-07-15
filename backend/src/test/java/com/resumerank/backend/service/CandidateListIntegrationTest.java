package com.resumerank.backend.service;

import com.resumerank.backend.dto.CandidateListResponse;
import com.resumerank.backend.entity.Candidate;
import com.resumerank.backend.entity.JobPosting;
import com.resumerank.backend.entity.User;
import com.resumerank.backend.entity.ResumeStatus;
import com.resumerank.backend.entity.PipelineStatus;
import com.resumerank.backend.entity.CandidateScore;
import com.resumerank.backend.entity.JobPostingStatus;
import com.resumerank.backend.entity.CandidateStatusLog;
import com.resumerank.backend.exception.ResourceNotFoundException;
import com.resumerank.backend.repository.CandidateRepository;
import com.resumerank.backend.repository.CandidateScoreRepository;
import com.resumerank.backend.repository.JobPostingRepository;
import com.resumerank.backend.repository.UserRepository;
import com.resumerank.backend.repository.CandidateStatusLogRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CandidateListIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.resumerank.backend.service.JwtService jwtService;

    @Autowired
    private CandidateService candidateService;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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

    private User recruiterA;
    private User recruiterB;
    private JobPosting postingA;

    @BeforeEach
    void setUp() {
        // Clear repositories to start fresh
        candidateScoreRepository.deleteAllInBatch();
        candidateRepository.deleteAllInBatch();
        jobPostingRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // 1. Create Recruiter A
        recruiterA = new User();
        recruiterA.setEmail("recruiterA@example.com");
        recruiterA.setPasswordHash("hashedpassword123");
        recruiterA = userRepository.saveAndFlush(recruiterA);

        // 2. Create Recruiter B
        recruiterB = new User();
        recruiterB.setEmail("recruiterB@example.com");
        recruiterB.setPasswordHash("hashedpassword456");
        recruiterB = userRepository.saveAndFlush(recruiterB);

        // 3. Create JobPosting for Recruiter A
        postingA = new JobPosting();
        postingA.setUser(recruiterA);
        postingA.setTitle("Java Backend Engineer");
        postingA.setDescription("Java developer with spring experience");
        postingA.setStatus(JobPostingStatus.ACTIVE);
        postingA.setRequiredSkills(new String[]{"Java", "Spring"});
        postingA.setNiceToHaveSkills(new String[]{"Postgres"});
        postingA.setMinYearsExperience(3);
        postingA = jobPostingRepository.saveAndFlush(postingA);
    }

    @Test
    void getCandidatesList_SortingScoreDesc_PutsScoredAboveUnscored() {
        // 1. Create older unscored candidate (Created 2 days ago)
        Candidate unscoredOld = new Candidate();
        unscoredOld.setJobPosting(postingA);
        unscoredOld.setResumeFileUrl("http://cloudinary.com/unscored-old.pdf");
        unscoredOld.setResumeStatus(ResumeStatus.PENDING);
        unscoredOld.setPipelineStatus(PipelineStatus.NEW);
        unscoredOld.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(2));
        unscoredOld = candidateRepository.saveAndFlush(unscoredOld);

        // 2. Create newer scored candidate (Created 1 day ago)
        Candidate scoredNew = new Candidate();
        scoredNew.setJobPosting(postingA);
        scoredNew.setResumeFileUrl("http://cloudinary.com/scored-new.pdf");
        scoredNew.setResumeStatus(ResumeStatus.SCORED);
        scoredNew.setPipelineStatus(PipelineStatus.NEW);
        scoredNew.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        scoredNew = candidateRepository.saveAndFlush(scoredNew);

        CandidateScore score = new CandidateScore();
        score.setCandidate(scoredNew);
        score.setOverallScore(85);
        score.setSkillsScore(85);
        score.setExperienceScore(85);
        score.setSeniorityScore(85);
        score.setScoredAt(OffsetDateTime.now(ZoneOffset.UTC));
        candidateScoreRepository.saveAndFlush(score);

        // Fetch list sorted by score_desc
        CandidateListResponse response = candidateService.getCandidatesList(
                recruiterA.getId(),
                postingA.getId(),
                "score_desc",
                null,
                null,
                null,
                null,
                null,
                10
        );

        // Assert scoredNew is first, and unscoredOld is second (Nulls last)
        Assertions.assertEquals(2, response.getItems().size());
        Assertions.assertEquals(scoredNew.getId(), response.getItems().get(0).getId());
        Assertions.assertEquals(unscoredOld.getId(), response.getItems().get(1).getId());
    }

    @Test
    void getCandidatesList_MinScoreFilter_ExcludesLowerScoredAndUnscored() {
        // 1. Candidate A: score = 85
        Candidate candidateA = new Candidate();
        candidateA.setJobPosting(postingA);
        candidateA.setResumeFileUrl("http://cloudinary.com/candidateA.pdf");
        candidateA.setResumeStatus(ResumeStatus.SCORED);
        candidateA = candidateRepository.saveAndFlush(candidateA);

        CandidateScore scoreA = new CandidateScore();
        scoreA.setCandidate(candidateA);
        scoreA.setOverallScore(85);
        scoreA.setSkillsScore(85);
        scoreA.setExperienceScore(85);
        scoreA.setSeniorityScore(85);
        scoreA.setScoredAt(OffsetDateTime.now(ZoneOffset.UTC));
        candidateScoreRepository.saveAndFlush(scoreA);

        // 2. Candidate B: score = 75
        Candidate candidateB = new Candidate();
        candidateB.setJobPosting(postingA);
        candidateB.setResumeFileUrl("http://cloudinary.com/candidateB.pdf");
        candidateB.setResumeStatus(ResumeStatus.SCORED);
        candidateB = candidateRepository.saveAndFlush(candidateB);

        CandidateScore scoreB = new CandidateScore();
        scoreB.setCandidate(candidateB);
        scoreB.setOverallScore(75);
        scoreB.setSkillsScore(75);
        scoreB.setExperienceScore(75);
        scoreB.setSeniorityScore(75);
        scoreB.setScoredAt(OffsetDateTime.now(ZoneOffset.UTC));
        candidateScoreRepository.saveAndFlush(scoreB);

        // 3. Candidate C: unscored (null)
        Candidate candidateC = new Candidate();
        candidateC.setJobPosting(postingA);
        candidateC.setResumeFileUrl("http://cloudinary.com/candidateC.pdf");
        candidateC.setResumeStatus(ResumeStatus.PENDING);
        candidateC = candidateRepository.saveAndFlush(candidateC);

        // Query with minScore = 80
        CandidateListResponse response = candidateService.getCandidatesList(
                recruiterA.getId(),
                postingA.getId(),
                "score_desc",
                80,
                null,
                null,
                null,
                null,
                10
        );

        // Assert only Candidate A is returned
        Assertions.assertEquals(1, response.getItems().size());
        Assertions.assertEquals(candidateA.getId(), response.getItems().get(0).getId());
    }

    @Test
    void getCandidatesList_UnauthorizedRecruiter_ThrowsResourceNotFoundException() {
        // Attempt to fetch candidates list for postingA using recruiterB (who does not own postingA)
        Assertions.assertThrows(ResourceNotFoundException.class, () -> {
            candidateService.getCandidatesList(
                    recruiterB.getId(),
                    postingA.getId(),
                    "score_desc",
                    null,
                    null,
                    null,
                    null,
                    null,
                    10
            );
        });
    }

    private void stubJwt(String token, UUID userId, String email) {
        com.auth0.jwt.interfaces.DecodedJWT decodedJWT = org.mockito.Mockito.mock(com.auth0.jwt.interfaces.DecodedJWT.class);
        org.mockito.Mockito.when(decodedJWT.getSubject()).thenReturn(userId.toString());

        com.auth0.jwt.interfaces.Claim emailClaim = org.mockito.Mockito.mock(com.auth0.jwt.interfaces.Claim.class);
        org.mockito.Mockito.when(emailClaim.asString()).thenReturn(email);
        org.mockito.Mockito.when(decodedJWT.getClaim("email")).thenReturn(emailClaim);

        com.auth0.jwt.interfaces.Claim typeClaim = org.mockito.Mockito.mock(com.auth0.jwt.interfaces.Claim.class);
        org.mockito.Mockito.when(typeClaim.asString()).thenReturn("access");
        org.mockito.Mockito.when(decodedJWT.getClaim("type")).thenReturn(typeClaim);

        org.mockito.Mockito.when(jwtService.verifyToken(token)).thenReturn(decodedJWT);
    }

    @Test
    void getCandidateDetail_Owner_ReturnsFullDetailsWithSummaryAndExperience() throws Exception {
        stubJwt("tokenA", recruiterA.getId(), recruiterA.getEmail());

        Candidate candidate = new Candidate();
        candidate.setJobPosting(postingA);
        candidate.setResumeFileUrl("http://cloudinary.com/detail.pdf");
        candidate.setResumeStatus(ResumeStatus.SCORED);
        candidate.setPipelineStatus(PipelineStatus.NEW);
        candidate = candidateRepository.saveAndFlush(candidate);

        CandidateScore score = new CandidateScore();
        score.setCandidate(candidate);
        score.setOverallScore(85);
        score.setSkillsScore(90);
        score.setExperienceScore(80);
        score.setSeniorityScore(85);
        score.setSummary("An excellent Java Engineer.");
        score.setYearsExperienceDetected(java.math.BigDecimal.valueOf(4.5));
        score.setScoredAt(OffsetDateTime.now(ZoneOffset.UTC));
        candidateScoreRepository.saveAndFlush(score);

        candidate.setCandidateScore(score);
        candidateRepository.saveAndFlush(candidate);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/candidates/" + candidate.getId())
                        .header("Authorization", "Bearer tokenA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(candidate.getId().toString()))
                .andExpect(jsonPath("$.overallScore").value(85))
                .andExpect(jsonPath("$.skillsScore").value(90))
                .andExpect(jsonPath("$.summary").value("An excellent Java Engineer."))
                .andExpect(jsonPath("$.yearsExperienceDetected").value(4.5));
    }

    @Test
    void getCandidateDetail_UnauthorizedAndNonExistent_MatchExactly() throws Exception {
        stubJwt("tokenB", recruiterB.getId(), recruiterB.getEmail());

        // Create a candidate for postingA (owned by recruiterA)
        Candidate candidate = new Candidate();
        candidate.setJobPosting(postingA);
        candidate.setResumeFileUrl("http://cloudinary.com/unauth.pdf");
        candidate.setResumeStatus(ResumeStatus.PENDING);
        candidate = candidateRepository.saveAndFlush(candidate);

        entityManager.flush();
        entityManager.clear();

        // Fetch candidate detail as recruiterB (should return 404 because recruiterB doesn't own postingA)
        String unauthResponse = mockMvc.perform(get("/api/candidates/" + candidate.getId())
                        .header("Authorization", "Bearer tokenB"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Fetch a genuinely nonexistent candidate ID
        UUID nonexistentId = UUID.randomUUID();
        String nonexistentResponse = mockMvc.perform(get("/api/candidates/" + nonexistentId)
                        .header("Authorization", "Bearer tokenB"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Deserialize to maps and remove the 'instance' key to compare the rest of the shape exactly
        java.util.Map<String, Object> map1 = objectMapper.readValue(unauthResponse, java.util.Map.class);
        java.util.Map<String, Object> map2 = objectMapper.readValue(nonexistentResponse, java.util.Map.class);
        map1.remove("instance");
        map2.remove("instance");

        Assertions.assertEquals(map2, map1);
    }

    @Test
    void getCandidateDetail_FailedResumeStatus_IncludesParseError() throws Exception {
        stubJwt("tokenA", recruiterA.getId(), recruiterA.getEmail());

        Candidate candidate = new Candidate();
        candidate.setJobPosting(postingA);
        candidate.setResumeFileUrl("http://cloudinary.com/failed.pdf");
        candidate.setResumeStatus(ResumeStatus.FAILED);
        candidate.setParseError("Could not extract text from document");
        candidate = candidateRepository.saveAndFlush(candidate);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/candidates/" + candidate.getId())
                        .header("Authorization", "Bearer tokenA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeStatus").value("FAILED"))
                .andExpect(jsonPath("$.parseError").value("Could not extract text from document"));
    }

    @Test
    void updateCandidateStatus_GenuineChange_UpdatesAndLogs() throws Exception {
        stubJwt("tokenA", recruiterA.getId(), recruiterA.getEmail());

        Candidate candidate = new Candidate();
        candidate.setJobPosting(postingA);
        candidate.setResumeFileUrl("http://cloudinary.com/status-test.pdf");
        candidate.setResumeStatus(ResumeStatus.PENDING);
        candidate.setPipelineStatus(PipelineStatus.NEW);
        candidate = candidateRepository.saveAndFlush(candidate);

        entityManager.flush();
        entityManager.clear();

        // 1. Perform genuine status transition (NEW -> REVIEWING)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/candidates/" + candidate.getId() + "/status")
                        .header("Authorization", "Bearer tokenA")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"reviewing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineStatus").value("REVIEWING"));

        entityManager.flush();
        entityManager.clear();

        // 2. Assert in DB that candidate is updated and 1 log entry is written
        Candidate updated = candidateRepository.findById(candidate.getId()).get();
        Assertions.assertEquals(PipelineStatus.REVIEWING, updated.getPipelineStatus());

        java.util.List<CandidateStatusLog> logs = candidateStatusLogRepository.findByCandidateIdOrderByCreatedAtDesc(candidate.getId());
        Assertions.assertEquals(1, logs.size());
        Assertions.assertEquals(PipelineStatus.NEW, logs.get(0).getFromStatus());
        Assertions.assertEquals(PipelineStatus.REVIEWING, logs.get(0).getToStatus());
        Assertions.assertEquals(recruiterA.getId(), logs.get(0).getChangedBy().getId());
    }

    @Test
    void updateCandidateStatus_NoOpChange_DoesNotLog() throws Exception {
        stubJwt("tokenA", recruiterA.getId(), recruiterA.getEmail());

        Candidate candidate = new Candidate();
        candidate.setJobPosting(postingA);
        candidate.setResumeFileUrl("http://cloudinary.com/status-noop.pdf");
        candidate.setResumeStatus(ResumeStatus.PENDING);
        candidate.setPipelineStatus(PipelineStatus.NEW);
        candidate = candidateRepository.saveAndFlush(candidate);

        entityManager.flush();
        entityManager.clear();

        // 1. First change (NEW -> REVIEWING) -> should create log
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/candidates/" + candidate.getId() + "/status")
                        .header("Authorization", "Bearer tokenA")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"reviewing\"}"))
                .andExpect(status().isOk());

        // 2. Second change (REVIEWING -> REVIEWING) -> should NOT create log
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/candidates/" + candidate.getId() + "/status")
                        .header("Authorization", "Bearer tokenA")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"reviewing\"}"))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        // Assert only 1 log exists total
        java.util.List<CandidateStatusLog> logs = candidateStatusLogRepository.findByCandidateIdOrderByCreatedAtDesc(candidate.getId());
        Assertions.assertEquals(1, logs.size());
    }

    @Test
    void getCandidateStatusLog_ReturnsNewestFirstWithEmail() throws Exception {
        stubJwt("tokenA", recruiterA.getId(), recruiterA.getEmail());

        Candidate candidate = new Candidate();
        candidate.setJobPosting(postingA);
        candidate.setResumeFileUrl("http://cloudinary.com/status-history.pdf");
        candidate.setResumeStatus(ResumeStatus.PENDING);
        candidate.setPipelineStatus(PipelineStatus.SHORTLISTED);
        candidate = candidateRepository.saveAndFlush(candidate);

        // Add history logs manually with distinct timestamps
        CandidateStatusLog log1 = new CandidateStatusLog();
        log1.setCandidate(candidate);
        log1.setChangedBy(recruiterA);
        log1.setFromStatus(PipelineStatus.NEW);
        log1.setToStatus(PipelineStatus.REVIEWING);
        log1.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        candidateStatusLogRepository.saveAndFlush(log1);

        CandidateStatusLog log2 = new CandidateStatusLog();
        log2.setCandidate(candidate);
        log2.setChangedBy(recruiterA);
        log2.setFromStatus(PipelineStatus.REVIEWING);
        log2.setToStatus(PipelineStatus.SHORTLISTED);
        log2.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        candidateStatusLogRepository.saveAndFlush(log2);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/candidates/" + candidate.getId() + "/status-log")
                        .header("Authorization", "Bearer tokenA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Newest first (log2)
                .andExpect(jsonPath("$[0].fromStatus").value("reviewing"))
                .andExpect(jsonPath("$[0].toStatus").value("shortlisted"))
                .andExpect(jsonPath("$[0].changedByEmail").value(recruiterA.getEmail()))
                // Oldest last (log1)
                .andExpect(jsonPath("$[1].fromStatus").value("new"))
                .andExpect(jsonPath("$[1].toStatus").value("reviewing"))
                .andExpect(jsonPath("$[1].changedByEmail").value(recruiterA.getEmail()));
    }

    @Test
    void updateBulkCandidateStatus_AllOwnedCandidates_Succeeds() throws Exception {
        stubJwt("tokenA", recruiterA.getId(), recruiterA.getEmail());

        Candidate c1 = new Candidate();
        c1.setJobPosting(postingA);
        c1.setResumeFileUrl("http://cloudinary.com/c1.pdf");
        c1.setPipelineStatus(PipelineStatus.NEW);
        c1 = candidateRepository.saveAndFlush(c1);

        Candidate c2 = new Candidate();
        c2.setJobPosting(postingA);
        c2.setResumeFileUrl("http://cloudinary.com/c2.pdf");
        c2.setPipelineStatus(PipelineStatus.NEW);
        c2 = candidateRepository.saveAndFlush(c2);

        Candidate c3 = new Candidate();
        c3.setJobPosting(postingA);
        c3.setResumeFileUrl("http://cloudinary.com/c3.pdf");
        c3.setPipelineStatus(PipelineStatus.NEW);
        c3 = candidateRepository.saveAndFlush(c3);

        entityManager.flush();
        entityManager.clear();

        String payload = String.format(
                "{\"candidateIds\":[\"%s\",\"%s\",\"%s\"],\"status\":\"shortlisted\"}",
                c1.getId(), c2.getId(), c3.getId()
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/candidates/bulk-status")
                        .header("Authorization", "Bearer tokenA")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated.length()").value(3))
                .andExpect(jsonPath("$.skipped.length()").value(0))
                .andExpect(jsonPath("$.updated[0]").value(c1.getId().toString()))
                .andExpect(jsonPath("$.updated[1]").value(c2.getId().toString()))
                .andExpect(jsonPath("$.updated[2]").value(c3.getId().toString()));

        entityManager.flush();
        entityManager.clear();

        // Assert candidate status updates and individual log records
        for (UUID id : java.util.List.of(c1.getId(), c2.getId(), c3.getId())) {
            Candidate updatedCand = candidateRepository.findById(id).get();
            Assertions.assertEquals(PipelineStatus.SHORTLISTED, updatedCand.getPipelineStatus());

            java.util.List<CandidateStatusLog> logs = candidateStatusLogRepository.findByCandidateIdOrderByCreatedAtDesc(id);
            Assertions.assertEquals(1, logs.size());
            Assertions.assertEquals(PipelineStatus.NEW, logs.get(0).getFromStatus());
            Assertions.assertEquals(PipelineStatus.SHORTLISTED, logs.get(0).getToStatus());
        }
    }

    @Test
    void updateBulkCandidateStatus_MixedOwnership_SucceedsPartially() throws Exception {
        stubJwt("tokenA", recruiterA.getId(), recruiterA.getEmail());

        // Create owned candidates
        Candidate c1 = new Candidate();
        c1.setJobPosting(postingA);
        c1.setResumeFileUrl("http://cloudinary.com/owned1.pdf");
        c1.setPipelineStatus(PipelineStatus.NEW);
        c1 = candidateRepository.saveAndFlush(c1);

        Candidate c2 = new Candidate();
        c2.setJobPosting(postingA);
        c2.setResumeFileUrl("http://cloudinary.com/owned2.pdf");
        c2.setPipelineStatus(PipelineStatus.NEW);
        c2 = candidateRepository.saveAndFlush(c2);

        // Create unowned candidate belonging to recruiterB
        JobPosting postingB = new JobPosting();
        postingB.setUser(recruiterB);
        postingB.setTitle("Other Job");
        postingB.setDescription("Other description");
        postingB.setStatus(JobPostingStatus.ACTIVE);
        postingB.setRequiredSkills(new String[]{"Java"});
        postingB.setMinYearsExperience(2);
        postingB = jobPostingRepository.saveAndFlush(postingB);

        Candidate cUnowned = new Candidate();
        cUnowned.setJobPosting(postingB);
        cUnowned.setResumeFileUrl("http://cloudinary.com/unowned.pdf");
        cUnowned.setPipelineStatus(PipelineStatus.NEW);
        cUnowned = candidateRepository.saveAndFlush(cUnowned);

        entityManager.flush();
        entityManager.clear();

        String payload = String.format(
                "{\"candidateIds\":[\"%s\",\"%s\",\"%s\"],\"status\":\"rejected\"}",
                c1.getId(), c2.getId(), cUnowned.getId()
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/candidates/bulk-status")
                        .header("Authorization", "Bearer tokenA")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated.length()").value(2))
                .andExpect(jsonPath("$.skipped.length()").value(1))
                .andExpect(jsonPath("$.updated[0]").value(c1.getId().toString()))
                .andExpect(jsonPath("$.updated[1]").value(c2.getId().toString()))
                .andExpect(jsonPath("$.skipped[0]").value(cUnowned.getId().toString()));

        entityManager.flush();
        entityManager.clear();

        // Assert owned are updated, unowned is NOT
        Assertions.assertEquals(PipelineStatus.REJECTED, candidateRepository.findById(c1.getId()).get().getPipelineStatus());
        Assertions.assertEquals(PipelineStatus.REJECTED, candidateRepository.findById(c2.getId()).get().getPipelineStatus());
        Assertions.assertEquals(PipelineStatus.NEW, candidateRepository.findById(cUnowned.getId()).get().getPipelineStatus());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void exportCandidates_OwnerRequest_SucceedsWithProperFormat() throws Exception {
        stubJwt("tokenA", recruiterA.getId(), recruiterA.getEmail());

        // Create a candidate with a comma in the name
        Candidate c1 = new Candidate();
        c1.setJobPosting(postingA);
        c1.setName("Doe, John");
        c1.setEmail("john.doe@example.com");
        c1.setResumeFileUrl("http://cloudinary.com/doe.pdf");
        c1.setResumeStatus(ResumeStatus.SCORED);
        c1.setPipelineStatus(PipelineStatus.SHORTLISTED);
        c1 = candidateRepository.saveAndFlush(c1);

        CandidateScore score1 = new CandidateScore();
        score1.setCandidate(c1);
        score1.setOverallScore(95);
        score1.setSkillsScore(90);
        score1.setExperienceScore(95);
        score1.setSeniorityScore(100);
        score1.setMatchedSkills(new String[]{"Java", "Spring Boot"});
        score1.setMissingSkills(new String[]{"Docker"});
        score1.setSummary("Great candidate!");
        score1.setScoredAt(java.time.OffsetDateTime.now());
        candidateScoreRepository.saveAndFlush(score1);

        org.springframework.test.web.servlet.MvcResult mvcResult = mockMvc.perform(get("/api/job-postings/" + postingA.getId() + "/candidates/export")
                        .header("Authorization", "Bearer tokenA"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
                .andReturn();

        String responseCsv = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment; filename=\"candidates-")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Parse and verify the CSV response
        org.apache.commons.csv.CSVParser csvParser = org.apache.commons.csv.CSVParser.parse(
                responseCsv,
                org.apache.commons.csv.CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build()
        );

        java.util.List<org.apache.commons.csv.CSVRecord> records = csvParser.getRecords();
        // Since we flushed and cleared L1 cache, postingA might have other candidates from before, but c1 must be present.
        boolean foundC1 = false;
        for (org.apache.commons.csv.CSVRecord record : records) {
            if ("john.doe@example.com".equals(record.get("Email"))) {
                foundC1 = true;
                Assertions.assertEquals("Doe, John", record.get("Name"));
                Assertions.assertEquals("95", record.get("Overall Score"));
                Assertions.assertEquals("90", record.get("Skills Score"));
                Assertions.assertEquals("95", record.get("Experience Score"));
                Assertions.assertEquals("100", record.get("Seniority Score"));
                Assertions.assertEquals("Java;Spring Boot", record.get("Matched Skills"));
                Assertions.assertEquals("Docker", record.get("Missing Skills"));
                Assertions.assertEquals("SHORTLISTED", record.get("Pipeline Status"));
            }
        }
        Assertions.assertTrue(foundC1);
    }

    @Test
    void exportCandidates_NonOwnerRequest_Returns404() throws Exception {
        stubJwt("tokenB", recruiterB.getId(), recruiterB.getEmail());

        mockMvc.perform(get("/api/job-postings/" + postingA.getId() + "/candidates/export")
                        .header("Authorization", "Bearer tokenB"))
                .andExpect(status().isNotFound());
    }
}
