package com.resumerank.backend.service;

import com.resumerank.backend.dto.CandidateCreateRequest;
import com.resumerank.backend.dto.CandidateResponse;
import com.resumerank.backend.entity.Candidate;
import com.resumerank.backend.entity.JobPosting;
import com.resumerank.backend.entity.PipelineStatus;
import com.resumerank.backend.entity.ResumeStatus;
import com.resumerank.backend.exception.ResourceNotFoundException;
import com.resumerank.backend.repository.CandidateRepository;
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
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${INTERNAL_SERVICE_TOKEN:}")
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

        Candidate candidate = new Candidate();
        candidate.setJobPosting(jobPosting);
        candidate.setResumeFileUrl(request.getResumeFileUrl());
        candidate.setResumeStatus(ResumeStatus.PENDING);
        candidate.setPipelineStatus(PipelineStatus.NEW);

        Candidate saved = candidateRepository.saveAndFlush(candidate);

        // Call async method on AOP proxy self reference to run asynchronously
        self.processCandidateResumeAsync(saved.getId());

        return mapToResponse(saved);
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
                candidate.getUpdatedAt()
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
}
