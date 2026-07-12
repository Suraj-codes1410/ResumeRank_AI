package com.resumerank.backend.service;

import com.resumerank.backend.dto.CandidateCreateRequest;
import com.resumerank.backend.dto.CandidateResponse;
import com.resumerank.backend.entity.Candidate;
import com.resumerank.backend.entity.JobPosting;
import com.resumerank.backend.entity.ResumeStatus;
import com.resumerank.backend.dto.AiWebhookPayload;
import com.resumerank.backend.entity.CandidateScore;
import com.resumerank.backend.entity.User;
import com.resumerank.backend.repository.CandidateRepository;
import com.resumerank.backend.repository.CandidateScoreRepository;
import com.resumerank.backend.repository.JobPostingRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class CandidateServiceTest {

    private CandidateRepository candidateRepository;
    private CandidateScoreRepository candidateScoreRepository;
    private JobPostingRepository jobPostingRepository;
    private RestTemplate restTemplate;
    private CandidateService candidateService;

    private UUID userId;
    private UUID jobPostingId;
    private User user;
    private JobPosting jobPosting;

    @BeforeEach
    void setUp() {
        candidateRepository = Mockito.mock(CandidateRepository.class);
        candidateScoreRepository = Mockito.mock(CandidateScoreRepository.class);
        jobPostingRepository = Mockito.mock(JobPostingRepository.class);
        restTemplate = Mockito.mock(RestTemplate.class);
        
        candidateService = new CandidateService();
        
        ReflectionTestUtils.setField(candidateService, "candidateRepository", candidateRepository);
        ReflectionTestUtils.setField(candidateService, "candidateScoreRepository", candidateScoreRepository);
        ReflectionTestUtils.setField(candidateService, "jobPostingRepository", jobPostingRepository);
        ReflectionTestUtils.setField(candidateService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(candidateService, "internalServiceToken", "test-token-123");
        ReflectionTestUtils.setField(candidateService, "aiServiceUrl", "http://localhost:8000");
        
        // Lazy self injection mock setup
        ReflectionTestUtils.setField(candidateService, "self", candidateService);

        userId = UUID.randomUUID();
        jobPostingId = UUID.randomUUID();

        user = new User();
        user.setId(userId);

        jobPosting = new JobPosting();
        jobPosting.setId(jobPostingId);
        jobPosting.setUser(user);
        jobPosting.setTitle("Software Engineer");
        jobPosting.setDescription("Java developer needed");
        jobPosting.setRequiredSkills(new String[]{"Java", "Spring"});
        jobPosting.setNiceToHaveSkills(new String[]{"Docker"});
        jobPosting.setMinYearsExperience(3);
    }

    @Test
    void createCandidate_Success_ReturnsPending() {
        Mockito.when(jobPostingRepository.findById(jobPostingId)).thenReturn(Optional.of(jobPosting));
        
        UUID candidateId = UUID.randomUUID();
        Mockito.when(candidateRepository.save(any(Candidate.class))).thenAnswer(invocation -> {
            Candidate c = invocation.getArgument(0);
            c.setId(candidateId);
            return c;
        });
        Mockito.when(candidateRepository.saveAndFlush(any(Candidate.class))).thenAnswer(invocation -> {
            Candidate c = invocation.getArgument(0);
            c.setId(candidateId);
            return c;
        });

        // Suppress actual async call in this creation test to isolate DB insert assertions
        CandidateService spyService = Mockito.spy(candidateService);
        Mockito.doNothing().when(spyService).processCandidateResumeAsync(any(UUID.class));
        ReflectionTestUtils.setField(spyService, "self", spyService);

        CandidateCreateRequest request = new CandidateCreateRequest("http://cloudinary.com/resume.pdf");
        CandidateResponse response = spyService.createCandidate(userId, jobPostingId, request);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(candidateId, response.getId());
        Assertions.assertEquals(ResumeStatus.PENDING, response.getResumeStatus());
        Assertions.assertEquals("http://cloudinary.com/resume.pdf", response.getResumeFileUrl());

        Mockito.verify(candidateRepository).saveAndFlush(any(Candidate.class));
    }

    @Test
    void processCandidateResume_FailAllAttempts_TransitionToFailed() {
        UUID candidateId = UUID.randomUUID();
        Candidate candidate = new Candidate();
        candidate.setId(candidateId);
        candidate.setJobPosting(jobPosting);
        candidate.setResumeFileUrl("http://cloudinary.com/resume.pdf");
        candidate.setResumeStatus(ResumeStatus.PENDING);

        Mockito.when(candidateRepository.findByIdWithJobPosting(candidateId)).thenReturn(Optional.of(candidate));
        
        // Mock restTemplate.postForEntity to throw exceptions for all 3 attempts
        Mockito.when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        // Running the async method synchronously in the test
        candidateService.processCandidateResumeAsync(candidateId);

        // Verify restTemplate was called exactly 3 times (1 initial + 2 retries)
        Mockito.verify(restTemplate, Mockito.times(3))
                .postForEntity(any(String.class), any(), eq(String.class));

        // Verify candidate ends up saved with FAILED status and correct parseError
        ArgumentCaptor<Candidate> candidateCaptor = ArgumentCaptor.forClass(Candidate.class);
        Mockito.verify(candidateRepository, Mockito.atLeastOnce()).save(candidateCaptor.capture());
        
        Candidate finalCandidateState = candidateCaptor.getValue();
        Assertions.assertEquals(ResumeStatus.FAILED, finalCandidateState.getResumeStatus());
        Assertions.assertEquals("AI service unavailable, please retry", finalCandidateState.getParseError());
    }

    @Test
    void handleAiWebhook_ValidSuccess_SavesScoreAndStatusScored() {
        UUID candidateId = UUID.randomUUID();
        Candidate candidate = new Candidate();
        candidate.setId(candidateId);
        candidate.setResumeStatus(ResumeStatus.PENDING);

        Mockito.when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        Mockito.when(candidateScoreRepository.findByCandidateId(candidateId)).thenReturn(Optional.empty());

        AiWebhookPayload payload = new AiWebhookPayload();
        payload.setCandidateId(candidateId.toString());
        payload.setSuccess(true);

        AiWebhookPayload.ScoreDto score = new AiWebhookPayload.ScoreDto();
        score.setOverallScore(85);
        score.setSkillsScore(90);
        score.setExperienceScore(80);
        score.setSeniorityScore(85);
        score.setMatchedSkills(java.util.List.of("Java", "Spring"));
        score.setMissingSkills(java.util.List.of("Docker"));
        score.setYearsExperienceDetected(3.5);
        score.setSummary("Great candidate match.");
        payload.setScore(score);

        candidateService.handleAiWebhook(payload);

        // Verify CandidateScore was created and saved
        ArgumentCaptor<CandidateScore> scoreCaptor = ArgumentCaptor.forClass(CandidateScore.class);
        Mockito.verify(candidateScoreRepository).saveAndFlush(scoreCaptor.capture());
        CandidateScore savedScore = scoreCaptor.getValue();

        Assertions.assertEquals(85, savedScore.getOverallScore());
        Assertions.assertEquals(90, savedScore.getSkillsScore());
        Assertions.assertArrayEquals(new String[]{"Java", "Spring"}, savedScore.getMatchedSkills());
        Assertions.assertEquals(java.math.BigDecimal.valueOf(3.5), savedScore.getYearsExperienceDetected());

        // Verify Candidate status was set to SCORED
        Mockito.verify(candidateRepository).saveAndFlush(candidate);
        Assertions.assertEquals(ResumeStatus.SCORED, candidate.getResumeStatus());
    }

    @Test
    void handleAiWebhook_InvalidScoreRange_FailsCandidate() {
        UUID candidateId = UUID.randomUUID();
        Candidate candidate = new Candidate();
        candidate.setId(candidateId);
        candidate.setResumeStatus(ResumeStatus.PENDING);

        Mockito.when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        AiWebhookPayload payload = new AiWebhookPayload();
        payload.setCandidateId(candidateId.toString());
        payload.setSuccess(true);

        AiWebhookPayload.ScoreDto score = new AiWebhookPayload.ScoreDto();
        score.setOverallScore(200); // Invalid (must be <= 100)
        score.setSkillsScore(90);
        score.setExperienceScore(80);
        score.setSeniorityScore(85);
        score.setMatchedSkills(java.util.List.of("Java"));
        score.setMissingSkills(java.util.List.of());
        score.setSummary("Overscored.");
        payload.setScore(score);

        candidateService.handleAiWebhook(payload);

        // Verify CandidateScore was NEVER saved due to validation block
        Mockito.verify(candidateScoreRepository, Mockito.never()).saveAndFlush(any());

        // Verify Candidate was marked as FAILED with parseError
        Mockito.verify(candidateRepository).saveAndFlush(candidate);
        Assertions.assertEquals(ResumeStatus.FAILED, candidate.getResumeStatus());
        Assertions.assertEquals("Validation failed for score payload", candidate.getParseError());
    }

    @Test
    void handleAiWebhook_CallTwice_UpdatesExistingScore() {
        UUID candidateId = UUID.randomUUID();
        Candidate candidate = new Candidate();
        candidate.setId(candidateId);
        candidate.setResumeStatus(ResumeStatus.PENDING);

        Mockito.when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        // Stub findByCandidateId to return an existing score
        CandidateScore existingScore = new CandidateScore();
        existingScore.setId(UUID.randomUUID());
        existingScore.setOverallScore(60);
        Mockito.when(candidateScoreRepository.findByCandidateId(candidateId)).thenReturn(Optional.of(existingScore));

        AiWebhookPayload payload = new AiWebhookPayload();
        payload.setCandidateId(candidateId.toString());
        payload.setSuccess(true);

        AiWebhookPayload.ScoreDto score = new AiWebhookPayload.ScoreDto();
        score.setOverallScore(95); // New updated score
        score.setSkillsScore(90);
        score.setExperienceScore(95);
        score.setSeniorityScore(90);
        score.setMatchedSkills(java.util.List.of("Java"));
        score.setMissingSkills(java.util.List.of());
        score.setSummary("Updated review.");
        payload.setScore(score);

        candidateService.handleAiWebhook(payload);

        // Verify CandidateScore was saved with the same existing object (no duplicate)
        ArgumentCaptor<CandidateScore> scoreCaptor = ArgumentCaptor.forClass(CandidateScore.class);
        Mockito.verify(candidateScoreRepository).saveAndFlush(scoreCaptor.capture());
        CandidateScore savedScore = scoreCaptor.getValue();

        Assertions.assertEquals(existingScore.getId(), savedScore.getId());
        Assertions.assertEquals(95, savedScore.getOverallScore());
    }
}
