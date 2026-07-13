package com.resumerank.backend.service;

import com.resumerank.backend.dto.CandidateCreateRequest;
import com.resumerank.backend.dto.CandidateResponse;
import com.resumerank.backend.dto.AiWebhookPayload;
import com.resumerank.backend.dto.CandidateListResponse;
import com.resumerank.backend.dto.KeysetCursor;
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

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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

    @Transactional(readOnly = true)
    public CandidateListResponse getCandidatesList(
            UUID userId,
            UUID jobPostingId,
            String sort,
            Integer minScore,
            String skill,
            String search,
            String resumeStatus,
            String cursor,
            Integer limit) {
        
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found"));

        if (!jobPosting.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Job posting not found");
        }

        int queryLimit = (limit == null || limit <= 0) ? 25 : Math.min(limit, 100);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c.id, c.name, c.email, c.resume_file_url, c.resume_status, c.pipeline_status, ")
           .append("c.created_at, c.updated_at, c.parse_error, ")
           .append("cs.overall_score, cs.skills_score, cs.experience_score, cs.seniority_score, cs.matched_skills, cs.missing_skills ")
           .append("FROM candidates c ")
           .append("LEFT JOIN candidate_scores cs ON c.id = cs.candidate_id ")
           .append("WHERE c.job_posting_id = :jobPostingId AND c.deleted_at IS NULL ");

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("jobPostingId", jobPostingId);

        if (minScore != null) {
            sql.append("AND cs.overall_score >= :minScore ");
            params.put("minScore", minScore);
        }

        if (skill != null && !skill.isBlank()) {
            sql.append("AND EXISTS (SELECT 1 FROM unnest(cs.matched_skills) s WHERE LOWER(s) = LOWER(:skill)) ");
            params.put("skill", skill);
        }

        if (search != null && !search.isBlank()) {
            sql.append("AND LOWER(c.name) LIKE LOWER(:search) ");
            params.put("search", "%" + search + "%");
        }

        if (resumeStatus != null && !resumeStatus.isBlank()) {
            sql.append("AND c.resume_status = :resumeStatus ");
            params.put("resumeStatus", resumeStatus);
        }

        KeysetCursor decodedCursor = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                byte[] decodedBytes = java.util.Base64.getUrlDecoder().decode(cursor);
                decodedCursor = objectMapper.readValue(decodedBytes, KeysetCursor.class);
            } catch (Exception e) {
                // Ignore invalid cursor
            }
        }

        String sortType = (sort == null || sort.isBlank()) ? "score_desc" : sort;
        if (decodedCursor != null) {
            UUID lastId = decodedCursor.getId();
            String lastSortVal = decodedCursor.getSortValue();

            if ("score_desc".equals(sortType)) {
                if (lastSortVal != null && !"null".equals(lastSortVal)) {
                    int lastScore = Integer.parseInt(lastSortVal);
                    sql.append("AND (cs.overall_score < :lastScore OR (cs.overall_score = :lastScore AND c.id < :lastId) OR cs.overall_score IS NULL) ");
                    params.put("lastScore", lastScore);
                    params.put("lastId", lastId);
                } else {
                    sql.append("AND (cs.overall_score IS NULL AND c.id < :lastId) ");
                    params.put("lastId", lastId);
                }
            } else if ("score_asc".equals(sortType)) {
                if (lastSortVal != null && !"null".equals(lastSortVal)) {
                    int lastScore = Integer.parseInt(lastSortVal);
                    sql.append("AND ((cs.overall_score > :lastScore AND cs.overall_score IS NOT NULL) OR (cs.overall_score = :lastScore AND c.id > :lastId) OR cs.overall_score IS NULL) ");
                    params.put("lastScore", lastScore);
                    params.put("lastId", lastId);
                } else {
                    sql.append("AND (cs.overall_score IS NULL AND c.id > :lastId) ");
                    params.put("lastId", lastId);
                }
            } else if ("newest".equals(sortType)) {
                java.time.OffsetDateTime lastCreated = java.time.OffsetDateTime.parse(lastSortVal);
                sql.append("AND (c.created_at < :lastCreated OR (c.created_at = :lastCreated AND c.id < :lastId)) ");
                params.put("lastCreated", lastCreated);
                params.put("lastId", lastId);
            } else if ("oldest".equals(sortType)) {
                java.time.OffsetDateTime lastCreated = java.time.OffsetDateTime.parse(lastSortVal);
                sql.append("AND (c.created_at > :lastCreated OR (c.created_at = :lastCreated AND c.id > :lastId)) ");
                params.put("lastCreated", lastCreated);
                params.put("lastId", lastId);
            }
        }

        if ("score_desc".equals(sortType)) {
            sql.append("ORDER BY cs.overall_score DESC NULLS LAST, c.id DESC ");
        } else if ("score_asc".equals(sortType)) {
            sql.append("ORDER BY cs.overall_score ASC NULLS LAST, c.id ASC ");
        } else if ("newest".equals(sortType)) {
            sql.append("ORDER BY c.created_at DESC, c.id DESC ");
        } else if ("oldest".equals(sortType)) {
            sql.append("ORDER BY c.created_at ASC, c.id ASC ");
        }

        sql.append("LIMIT :limit ");
        params.put("limit", queryLimit);

        jakarta.persistence.Query query = entityManager.createNativeQuery(sql.toString());
        for (java.util.Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }

        java.util.List<Object[]> resultList = query.getResultList();
        java.util.List<CandidateResponse> items = new java.util.ArrayList<>();

        for (Object[] row : resultList) {
            UUID id = (UUID) row[0];
            String name = (String) row[1];
            String email = (String) row[2];
            String resumeFileUrl = (String) row[3];
            ResumeStatus resumeStatusEnum = ResumeStatus.valueOf((String) row[4]);
            PipelineStatus pipelineStatusEnum = PipelineStatus.valueOf((String) row[5]);

            java.time.OffsetDateTime createdAt = null;
            if (row[6] instanceof java.sql.Timestamp) {
                createdAt = ((java.sql.Timestamp) row[6]).toInstant().atOffset(java.time.ZoneOffset.UTC);
            } else if (row[6] instanceof java.time.OffsetDateTime) {
                createdAt = (java.time.OffsetDateTime) row[6];
            }

            java.time.OffsetDateTime updatedAt = null;
            if (row[7] instanceof java.sql.Timestamp) {
                updatedAt = ((java.sql.Timestamp) row[7]).toInstant().atOffset(java.time.ZoneOffset.UTC);
            } else if (row[7] instanceof java.time.OffsetDateTime) {
                updatedAt = (java.time.OffsetDateTime) row[7];
            }

            String parseError = (String) row[8];
            Integer overallScore = (Integer) row[9];
            Integer skillsScore = (Integer) row[10];
            Integer experienceScore = (Integer) row[11];
            Integer seniorityScore = (Integer) row[12];

            java.util.List<String> matchedSkills = null;
            java.util.List<String> missingSkills = null;

            try {
                java.sql.Array matchedArr = (java.sql.Array) row[13];
                if (matchedArr != null) {
                    matchedSkills = java.util.Arrays.asList((String[]) matchedArr.getArray());
                }
                java.sql.Array missingArr = (java.sql.Array) row[14];
                if (missingArr != null) {
                    missingSkills = java.util.Arrays.asList((String[]) missingArr.getArray());
                }
            } catch (Exception e) {
                // Ignore conversion errors
            }

            CandidateResponse response = new CandidateResponse();
            response.setId(id);
            response.setJobPostingId(jobPostingId);
            response.setName(name);
            response.setEmail(email);
            response.setResumeFileUrl(resumeFileUrl);
            response.setResumeStatus(resumeStatusEnum);
            response.setPipelineStatus(pipelineStatusEnum);
            response.setCreatedAt(createdAt);
            response.setUpdatedAt(updatedAt);
            response.setParseError(parseError);
            response.setOverallScore(overallScore);
            response.setSkillsScore(skillsScore);
            response.setExperienceScore(experienceScore);
            response.setSeniorityScore(seniorityScore);
            response.setMatchedSkills(matchedSkills);
            response.setMissingSkills(missingSkills);

            items.add(response);
        }

        String nextCursor = null;
        if (items.size() == queryLimit) {
            CandidateResponse lastItem = items.get(items.size() - 1);
            String lastSortVal = null;
            if ("score_desc".equals(sortType) || "score_asc".equals(sortType)) {
                lastSortVal = lastItem.getOverallScore() != null ? lastItem.getOverallScore().toString() : "null";
            } else {
                lastSortVal = lastItem.getCreatedAt().toString();
            }

            KeysetCursor cursorObj = new KeysetCursor(lastItem.getId(), lastSortVal, sortType);
            try {
                byte[] bytes = objectMapper.writeValueAsBytes(cursorObj);
                nextCursor = java.util.Base64.getUrlEncoder().encodeToString(bytes);
            } catch (Exception e) {
                // Ignore serialization error
            }
        }

        return new CandidateListResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public CandidateResponse getCandidateDetail(UUID userId, UUID candidateId) {
        Candidate candidate = candidateRepository.findByIdWithJobPostingAndScore(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found"));

        if (!candidate.getJobPosting().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Candidate not found");
        }

        return mapToResponse(candidate);
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
        Integer overallScore = null;
        Integer skillsScore = null;
        Integer experienceScore = null;
        Integer seniorityScore = null;
        java.util.List<String> matchedSkills = null;
        java.util.List<String> missingSkills = null;
        String summary = null;
        java.math.BigDecimal yearsExperienceDetected = null;

        CandidateScore score = candidate.getCandidateScore();
        if (score != null) {
            overallScore = score.getOverallScore();
            skillsScore = score.getSkillsScore();
            experienceScore = score.getExperienceScore();
            seniorityScore = score.getSeniorityScore();
            if (score.getMatchedSkills() != null) {
                matchedSkills = java.util.Arrays.asList(score.getMatchedSkills());
            }
            if (score.getMissingSkills() != null) {
                missingSkills = java.util.Arrays.asList(score.getMissingSkills());
            }
            summary = score.getSummary();
            yearsExperienceDetected = score.getYearsExperienceDetected();
        }

        CandidateResponse response = new CandidateResponse(
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
        response.setSkillsScore(skillsScore);
        response.setExperienceScore(experienceScore);
        response.setSeniorityScore(seniorityScore);
        response.setMatchedSkills(matchedSkills);
        response.setMissingSkills(missingSkills);
        response.setSummary(summary);
        response.setYearsExperienceDetected(yearsExperienceDetected);
        return response;
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
