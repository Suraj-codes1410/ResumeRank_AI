package com.resumerank.backend.service;

import com.resumerank.backend.dto.CandidateCreateRequest;
import com.resumerank.backend.dto.CandidateResponse;
import com.resumerank.backend.dto.AiWebhookPayload;
import com.resumerank.backend.entity.Candidate;
import com.resumerank.backend.entity.CandidateScore;
import com.resumerank.backend.entity.JobPosting;
import com.resumerank.backend.entity.PipelineStatus;
import com.resumerank.backend.entity.ResumeStatus;
import com.resumerank.backend.exception.ResourceNotFoundException;
import com.resumerank.backend.repository.CandidateRepository;
import com.resumerank.backend.repository.CandidateScoreRepository;
import com.resumerank.backend.repository.JobPostingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
public class CandidateService {

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private CandidateScoreRepository candidateScoreRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${INTERNAL_SERVICE_TOKEN:5ec834ec8d0b81d070cde05c99231c6bab517c11f510cf5139353204841b42b8}")
    private String internalServiceToken;

    @Value("${AI_SERVICE_URL:http://localhost:8000}")
    private String aiServiceUrl;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private CandidateService self;

    @Transactional
    public CandidateResponse createCandidate(UUID userId, UUID jobPostingId, CandidateCreateRequest request) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found"));

        if (!jobPosting.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Job posting not found");
        }

        UUID duplicateOf = null;
        if (request.getResumeHash() != null && !request.getResumeHash().isBlank()) {
            java.util.List<Candidate> duplicates = candidateRepository.findByJobPostingIdAndResumeHash(jobPostingId, request.getResumeHash());
            if (!duplicates.isEmpty()) {
                duplicateOf = duplicates.get(0).getId();
            }
        }

        Candidate candidate = new Candidate();
        candidate.setJobPosting(jobPosting);
        candidate.setResumeFileUrl(request.getResumeFileUrl());
        candidate.setResumeHash(request.getResumeHash());
        candidate.setResumeStatus(ResumeStatus.PENDING);
        candidate.setPipelineStatus(PipelineStatus.NEW);

        Candidate saved = candidateRepository.saveAndFlush(candidate);

        // Call async method on AOP proxy self reference to run asynchronously
        self.processCandidateResumeAsync(saved.getId());

        CandidateResponse response = mapToResponse(saved);
        response.setDuplicateOfCandidateId(duplicateOf);
        return response;
    }

    @Transactional(readOnly = true)
    public java.util.List<CandidateResponse> getCandidatesForJobPosting(UUID userId, UUID jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found"));

        if (!jobPosting.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Job posting not found");
        }

        return candidateRepository.findByJobPostingIdWithScore(jobPostingId).stream()
                .map(this::mapToResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Async
    public void processCandidateResumeAsync(UUID candidateId) {
        Candidate candidate = candidateRepository.findByIdWithJobPosting(candidateId).orElse(null);
        if (candidate == null) {
            return;
        }

        JobPosting jobPosting = candidate.getJobPosting();
        String url = aiServiceUrl + "/internal/process-resume";

        ProcessResumeRequest payload = new ProcessResumeRequest(
                candidate.getId().toString(),
                candidate.getResumeFileUrl(),
                jobPosting.getTitle(),
                jobPosting.getDescription(),
                jobPosting.getRequiredSkills(),
                jobPosting.getNiceToHaveSkills(),
                jobPosting.getMinYearsExperience()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalServiceToken);

        HttpEntity<ProcessResumeRequest> entity = new HttpEntity<>(payload, headers);

        int maxAttempts = 3;
        long[] backoffs = {2000, 5000};
        boolean success = false;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.postForEntity(url, entity, String.class);
                success = true;
                break;
            } catch (Exception e) {
                System.err.println("Attempt " + attempt + " failed to call AI service: " + e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoffs[attempt - 1]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (!success) {
            Candidate toUpdate = candidateRepository.findById(candidateId).orElse(candidate);
            toUpdate.setResumeStatus(ResumeStatus.FAILED);
            toUpdate.setParseError("AI service unavailable, please retry");
            candidateRepository.save(toUpdate);
        }
    }

    private CandidateResponse mapToResponse(Candidate candidate) {
        Integer overallScore = candidate.getCandidateScore() != null 
                ? candidate.getCandidateScore().getOverallScore() 
                : null;
        return new CandidateResponse(
                candidate.getId(),
                candidate.getJobPosting().getId(),
                candidate.getName(),
                candidate.getEmail(),
                candidate.getResumeFileUrl(),
                candidate.getResumeStatus(),
                candidate.getParseError(),
                candidate.getPipelineStatus(),
                candidate.getCreatedAt(),
                candidate.getUpdatedAt(),
                overallScore
        );
    }

    public static class ProcessResumeRequest {
        public String candidateId;
        public String fileUrl;
        public String jobTitle;
        public String jobDescription;
        public String[] requiredSkills;
        public String[] niceToHaveSkills;
        public Integer minYearsExperience;

        public ProcessResumeRequest(String candidateId, String fileUrl, String jobTitle, String jobDescription,
                                    String[] requiredSkills, String[] niceToHaveSkills, Integer minYearsExperience) {
            this.candidateId = candidateId;
            this.fileUrl = fileUrl;
            this.jobTitle = jobTitle;
            this.jobDescription = jobDescription;
            this.requiredSkills = requiredSkills;
            this.niceToHaveSkills = niceToHaveSkills;
            this.minYearsExperience = minYearsExperience;
        }
    }

    @Transactional
    public void handleAiWebhook(AiWebhookPayload payload) {
        UUID candidateId = UUID.fromString(payload.getCandidateId());
        
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found"));

        if (!payload.isSuccess()) {
            candidate.setResumeStatus(ResumeStatus.FAILED);
            candidate.setParseError(payload.getError());
            candidateRepository.saveAndFlush(candidate);
            return;
        }

        // Validate payload score (defense in depth validation crossing service boundary)
        AiWebhookPayload.ScoreDto scoreDto = payload.getScore();
        if (scoreDto == null || !validateScorePayload(scoreDto)) {
            System.err.println("Score payload validation failed for candidate: " + candidateId);
            candidate.setResumeStatus(ResumeStatus.FAILED);
            candidate.setParseError("Validation failed for score payload");
            candidateRepository.saveAndFlush(candidate);
            return;
        }

        // Upsert CandidateScore: retrieve existing score by candidate ID or create a new one
        CandidateScore candidateScore = candidateScoreRepository.findByCandidateId(candidateId)
                .orElse(new CandidateScore());
        
        candidateScore.setCandidate(candidate);
        candidateScore.setOverallScore(scoreDto.getOverallScore());
        candidateScore.setSkillsScore(scoreDto.getSkillsScore());
        candidateScore.setExperienceScore(scoreDto.getExperienceScore());
        candidateScore.setSeniorityScore(scoreDto.getSeniorityScore());
        
        String[] matchedArray = scoreDto.getMatchedSkills() != null 
                ? scoreDto.getMatchedSkills().toArray(new String[0]) 
                : new String[0];
        String[] missingArray = scoreDto.getMissingSkills() != null 
                ? scoreDto.getMissingSkills().toArray(new String[0]) 
                : new String[0];
        
        candidateScore.setMatchedSkills(matchedArray);
        candidateScore.setMissingSkills(missingArray);
        
        if (scoreDto.getYearsExperienceDetected() != null) {
            candidateScore.setYearsExperienceDetected(java.math.BigDecimal.valueOf(scoreDto.getYearsExperienceDetected()));
        } else {
            candidateScore.setYearsExperienceDetected(null);
        }
        
        candidateScore.setSummary(scoreDto.getSummary());
        candidateScore.setScoredAt(java.time.OffsetDateTime.now());

        candidateScoreRepository.saveAndFlush(candidateScore);

        // Update candidate status to SCORED
        candidate.setResumeStatus(ResumeStatus.SCORED);
        candidateRepository.saveAndFlush(candidate);
    }

    private boolean validateScorePayload(AiWebhookPayload.ScoreDto score) {
        if (score.getOverallScore() == null || score.getOverallScore() < 0 || score.getOverallScore() > 100) return false;
        if (score.getSkillsScore() == null || score.getSkillsScore() < 0 || score.getSkillsScore() > 100) return false;
        if (score.getExperienceScore() == null || score.getExperienceScore() < 0 || score.getExperienceScore() > 100) return false;
        if (score.getSeniorityScore() == null || score.getSeniorityScore() < 0 || score.getSeniorityScore() > 100) return false;
        if (score.getMatchedSkills() == null) return false;
        if (score.getMissingSkills() == null) return false;
        if (score.getSummary() == null || score.getSummary().trim().isEmpty()) return false;
        return true;
    }
}
