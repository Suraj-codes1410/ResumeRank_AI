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

    @Test
    void createCandidate_DuplicateResumeHash_ReturnsDuplicateOfCandidateId() {
        Mockito.when(jobPostingRepository.findById(jobPostingId)).thenReturn(Optional.of(jobPosting));

        UUID firstCandidateId = UUID.randomUUID();
        UUID secondCandidateId = UUID.randomUUID();
        String resumeHash = "md5-resume-hash-123";

        Candidate firstCandidate = new Candidate();
        firstCandidate.setId(firstCandidateId);
        firstCandidate.setJobPosting(jobPosting);
        firstCandidate.setResumeFileUrl("http://cloudinary.com/resume-first.pdf");
        firstCandidate.setResumeHash(resumeHash);

        // When searching for duplicates, return the first candidate
        Mockito.when(candidateRepository.findByJobPostingIdAndResumeHash(jobPostingId, resumeHash))
                .thenReturn(java.util.List.of(firstCandidate));

        Mockito.when(candidateRepository.saveAndFlush(any(Candidate.class))).thenAnswer(invocation -> {
            Candidate c = invocation.getArgument(0);
            c.setId(secondCandidateId);
            return c;
        });

        // Suppress actual async call
        CandidateService spyService = Mockito.spy(candidateService);
        Mockito.doNothing().when(spyService).processCandidateResumeAsync(any(UUID.class));
        ReflectionTestUtils.setField(spyService, "self", spyService);

        CandidateCreateRequest request = new CandidateCreateRequest("http://cloudinary.com/resume-second.pdf", resumeHash);
        CandidateResponse response = spyService.createCandidate(userId, jobPostingId, request);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(secondCandidateId, response.getId());
        Assertions.assertEquals(firstCandidateId, response.getDuplicateOfCandidateId());
    }

    @Test
    void processCandidateResumeAsync_IndependentConcurrency_NoInterference() {
        UUID candidateSuccessId = UUID.randomUUID();
        Candidate candidateSuccess = new Candidate();
        candidateSuccess.setId(candidateSuccessId);
        candidateSuccess.setJobPosting(jobPosting);
        candidateSuccess.setResumeFileUrl("http://cloudinary.com/resume-success.pdf");
        candidateSuccess.setResumeStatus(ResumeStatus.PENDING);

        UUID candidateFailId = UUID.randomUUID();
        Candidate candidateFail = new Candidate();
        candidateFail.setId(candidateFailId);
        candidateFail.setJobPosting(jobPosting);
        candidateFail.setResumeFileUrl("http://cloudinary.com/resume-fail.pdf");
        candidateFail.setResumeStatus(ResumeStatus.PENDING);

        Mockito.when(candidateRepository.findByIdWithJobPosting(candidateSuccessId)).thenReturn(Optional.of(candidateSuccess));
        Mockito.when(candidateRepository.findByIdWithJobPosting(candidateFailId)).thenReturn(Optional.of(candidateFail));

        Mockito.when(candidateRepository.findById(candidateSuccessId)).thenReturn(Optional.of(candidateSuccess));
        Mockito.when(candidateRepository.findById(candidateFailId)).thenReturn(Optional.of(candidateFail));

        // Mock restTemplate.postForEntity:
        // - For candidateSuccess: returns 200 (Success)
        // - For candidateFail: throws exception (Fail)
        Mockito.when(restTemplate.postForEntity(any(String.class), any(), eq(String.class))).thenAnswer(invocation -> {
            org.springframework.http.HttpEntity<?> entity = invocation.getArgument(1);
            Object body = entity.getBody();
            String candidateId = (String) ReflectionTestUtils.getField(body, "candidateId");
            if (candidateFailId.toString().equals(candidateId)) {
                throw new RestClientException("Connection refused");
            }
            return new org.springframework.http.ResponseEntity<>("OK", org.springframework.http.HttpStatus.OK);
        });

        // Run both async calls in quick succession
        candidateService.processCandidateResumeAsync(candidateSuccessId);
        candidateService.processCandidateResumeAsync(candidateFailId);

        // Verify status updates
        // The successful one should still be PENDING (waiting for webhook)
        Assertions.assertEquals(ResumeStatus.PENDING, candidateSuccess.getResumeStatus());
        
        // The failed one should be FAILED
        ArgumentCaptor<Candidate> candidateCaptor = ArgumentCaptor.forClass(Candidate.class);
        Mockito.verify(candidateRepository, Mockito.atLeastOnce()).save(candidateCaptor.capture());
        
        boolean foundFailed = candidateCaptor.getAllValues().stream()
                .anyMatch(c -> c.getId().equals(candidateFailId) && c.getResumeStatus() == ResumeStatus.FAILED);
        Assertions.assertTrue(foundFailed);
    }

    @Test
    void handleAiWebhook_FailedWebhookPayload_TransitionsToFailed() {
        UUID candidateId = UUID.randomUUID();
        Candidate candidate = new Candidate();
        candidate.setId(candidateId);
        candidate.setResumeStatus(ResumeStatus.PENDING);

        Mockito.when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        AiWebhookPayload payload = new AiWebhookPayload();
        payload.setCandidateId(candidateId.toString());
        payload.setSuccess(false); // Indicates AI service failure
        payload.setError("FastAPI: Out of memory during processing");

        candidateService.handleAiWebhook(payload);

        // Verify status transitions to FAILED
        Mockito.verify(candidateRepository).saveAndFlush(candidate);
        Assertions.assertEquals(ResumeStatus.FAILED, candidate.getResumeStatus());
        Assertions.assertEquals("FastAPI: Out of memory during processing", candidate.getParseError());
    }

    @Test
    void handleAiWebhook_MissingScorePayload_TransitionsToFailed() {
        UUID candidateId = UUID.randomUUID();
        Candidate candidate = new Candidate();
        candidate.setId(candidateId);
        candidate.setResumeStatus(ResumeStatus.PENDING);

        Mockito.when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        AiWebhookPayload payload = new AiWebhookPayload();
        payload.setCandidateId(candidateId.toString());
        payload.setSuccess(true);
        payload.setScore(null); // Missing score payload!

        candidateService.handleAiWebhook(payload);

        // Verify status transitions to FAILED
        Mockito.verify(candidateRepository).saveAndFlush(candidate);
        Assertions.assertEquals(ResumeStatus.FAILED, candidate.getResumeStatus());
        Assertions.assertEquals("Validation failed for score payload", candidate.getParseError());
    }

    @Test
    void processCandidateResume_RetrySuccess_TransitionsToPending() {
        UUID candidateId = UUID.randomUUID();
        Candidate candidate = new Candidate();
        candidate.setId(candidateId);
        candidate.setJobPosting(jobPosting);
        candidate.setResumeFileUrl("http://cloudinary.com/resume.pdf");
        candidate.setResumeStatus(ResumeStatus.PENDING);

        Mockito.when(candidateRepository.findByIdWithJobPosting(candidateId)).thenReturn(Optional.of(candidate));

        // Mock restTemplate: fail first attempt, succeed second attempt
        Mockito.when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenThrow(new RestClientException("Attempt 1 Timeout"))
                .thenReturn(new org.springframework.http.ResponseEntity<>("OK", org.springframework.http.HttpStatus.OK));

        candidateService.processCandidateResumeAsync(candidateId);

        // Verify called exactly twice
        Mockito.verify(restTemplate, Mockito.times(2))
                .postForEntity(any(String.class), any(), eq(String.class));

        // Verify status remains PENDING and did not transition to FAILED
        Assertions.assertEquals(ResumeStatus.PENDING, candidate.getResumeStatus());
        Assertions.assertNull(candidate.getParseError());
    }

    @Test
    void processCandidateResume_TimeoutOrResourceAccessExceptionTriggersRetry() {
        UUID candidateId = UUID.randomUUID();
        Candidate candidate = new Candidate();
        candidate.setId(candidateId);
        candidate.setJobPosting(jobPosting);
        candidate.setResumeFileUrl("http://cloudinary.com/resume.pdf");
        candidate.setResumeStatus(ResumeStatus.PENDING);

        Mockito.when(candidateRepository.findByIdWithJobPosting(candidateId)).thenReturn(Optional.of(candidate));

        // Mock restTemplate: throw resource access exceptions (timeouts)
        Mockito.when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Read timed out"));

        candidateService.processCandidateResumeAsync(candidateId);

        // Verify called exactly 3 times (1 initial + 2 retries)
        Mockito.verify(restTemplate, Mockito.times(3))
                .postForEntity(any(String.class), any(), eq(String.class));

        // Verify status transitions to FAILED
        ArgumentCaptor<Candidate> candidateCaptor = ArgumentCaptor.forClass(Candidate.class);
        Mockito.verify(candidateRepository, Mockito.atLeastOnce()).save(candidateCaptor.capture());
        
        Candidate finalCandidateState = candidateCaptor.getValue();
        Assertions.assertEquals(ResumeStatus.FAILED, finalCandidateState.getResumeStatus());
        Assertions.assertEquals("AI service unavailable, please retry", finalCandidateState.getParseError());
    }
}
