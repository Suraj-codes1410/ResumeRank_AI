package com.resumerank.backend.service;

import com.resumerank.backend.dto.JobPostingCreateRequest;
import com.resumerank.backend.dto.JobPostingResponse;
import com.resumerank.backend.entity.JobPosting;
import com.resumerank.backend.entity.JobPostingStatus;
import com.resumerank.backend.entity.User;
import com.resumerank.backend.repository.JobPostingRepository;
import com.resumerank.backend.repository.UserRepository;
import com.resumerank.backend.exception.ResourceNotFoundException;
import com.resumerank.backend.entity.SeniorityLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public JobPostingService(JobPostingRepository jobPostingRepository, 
                             UserRepository userRepository,
                             com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.jobPostingRepository = jobPostingRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobPostingResponse createJobPosting(UUID userId, JobPostingCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        JobPosting jobPosting = new JobPosting();
        jobPosting.setUser(user);
        jobPosting.setTitle(request.title());
        jobPosting.setDescription(request.description());
        jobPosting.setRequiredSkills(request.requiredSkills() != null ? request.requiredSkills() : new String[0]);
        jobPosting.setNiceToHaveSkills(request.niceToHaveSkills() != null ? request.niceToHaveSkills() : new String[0]);
        jobPosting.setMinYearsExperience(request.minYearsExperience());
        jobPosting.setSeniorityLevel(request.seniorityLevel());
        jobPosting.setStatus(JobPostingStatus.ACTIVE);

        JobPosting saved = jobPostingRepository.saveAndFlush(jobPosting);

        return new JobPostingResponse(
            saved.getId(),
            saved.getUser().getId(),
            saved.getTitle(),
            saved.getDescription(),
            saved.getRequiredSkills(),
            saved.getNiceToHaveSkills(),
            saved.getMinYearsExperience(),
            saved.getSeniorityLevel(),
            saved.getStatus(),
            saved.getCreatedAt(),
            saved.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public com.resumerank.backend.dto.JobPostingListResponse listJobPostings(
            UUID userId,
            com.resumerank.backend.entity.JobPostingStatus status,
            int page,
            int size) {
        
        int clampedSize = Math.min(size, 100);
        
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.asc("status"),
                org.springframework.data.domain.Sort.Order.desc("createdAt")
        );
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, clampedSize, sort);
        
        org.springframework.data.domain.Page<JobPosting> pageResult = jobPostingRepository.findByUserIdAndOptionalStatus(userId, status, pageable);
        
        java.util.List<JobPostingResponse> items = pageResult.getContent().stream()
                .map(saved -> new JobPostingResponse(
                        saved.getId(),
                        saved.getUser().getId(),
                        saved.getTitle(),
                        saved.getDescription(),
                        saved.getRequiredSkills(),
                        saved.getNiceToHaveSkills(),
                        saved.getMinYearsExperience(),
                        saved.getSeniorityLevel(),
                        saved.getStatus(),
                        saved.getCreatedAt(),
                        saved.getUpdatedAt()
                ))
                .toList();
                
        return new com.resumerank.backend.dto.JobPostingListResponse(
                items,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public JobPostingResponse getJobPosting(UUID userId, UUID id) {
        System.out.println("[DEBUG] getJobPosting - Requested ID: " + id);
        System.out.println("[DEBUG] getJobPosting - Token User ID: " + userId);
        
        JobPosting jobPosting = jobPostingRepository.findById(id)
                .orElse(null);
                
        if (jobPosting == null) {
            System.out.println("[DEBUG] getJobPosting - Record not found in DB!");
            throw new ResourceNotFoundException("Job posting not found");
        }
        
        System.out.println("[DEBUG] getJobPosting - Record found in DB! Owner User ID: " + jobPosting.getUser().getId());

        if (!jobPosting.getUser().getId().equals(userId)) {
            System.out.println("[DEBUG] getJobPosting - Owner ID mismatch! Access Denied (returning 404).");
            throw new ResourceNotFoundException("Job posting not found");
        }

        return new JobPostingResponse(
                jobPosting.getId(),
                jobPosting.getUser().getId(),
                jobPosting.getTitle(),
                jobPosting.getDescription(),
                jobPosting.getRequiredSkills(),
                jobPosting.getNiceToHaveSkills(),
                jobPosting.getMinYearsExperience(),
                jobPosting.getSeniorityLevel(),
                jobPosting.getStatus(),
                jobPosting.getCreatedAt(),
                jobPosting.getUpdatedAt()
        );
    }

    @Transactional
    public JobPostingResponse updateJobPosting(UUID userId, UUID id, com.fasterxml.jackson.databind.JsonNode patchNode) {
        JobPosting jobPosting = jobPostingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found"));

        if (!jobPosting.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Job posting not found");
        }

        if (patchNode.has("title")) {
            com.fasterxml.jackson.databind.JsonNode titleNode = patchNode.get("title");
            if (titleNode.isNull() || titleNode.asText().trim().isEmpty()) {
                throw new IllegalArgumentException("Title cannot be empty");
            }
            String title = titleNode.asText();
            if (title.length() > 250) {
                throw new IllegalArgumentException("Title must not exceed 250 characters");
            }
            jobPosting.setTitle(title);
        }

        if (patchNode.has("description")) {
            com.fasterxml.jackson.databind.JsonNode descNode = patchNode.get("description");
            if (descNode.isNull() || descNode.asText().trim().isEmpty()) {
                throw new IllegalArgumentException("Description cannot be empty");
            }
            String description = descNode.asText();
            if (description.length() > 10000) {
                throw new IllegalArgumentException("Description must not exceed 10000 characters");
            }
            jobPosting.setDescription(description);
        }

        if (patchNode.has("requiredSkills")) {
            com.fasterxml.jackson.databind.JsonNode skillsNode = patchNode.get("requiredSkills");
            if (skillsNode.isNull()) {
                jobPosting.setRequiredSkills(new String[0]);
            } else {
                try {
                    String[] skills = objectMapper.treeToValue(skillsNode, String[].class);
                    jobPosting.setRequiredSkills(skills != null ? skills : new String[0]);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid requiredSkills format");
                }
            }
        }

        if (patchNode.has("niceToHaveSkills")) {
            com.fasterxml.jackson.databind.JsonNode skillsNode = patchNode.get("niceToHaveSkills");
            if (skillsNode.isNull()) {
                jobPosting.setNiceToHaveSkills(new String[0]);
            } else {
                try {
                    String[] skills = objectMapper.treeToValue(skillsNode, String[].class);
                    jobPosting.setNiceToHaveSkills(skills != null ? skills : new String[0]);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid niceToHaveSkills format");
                }
            }
        }

        if (patchNode.has("minYearsExperience")) {
            com.fasterxml.jackson.databind.JsonNode expNode = patchNode.get("minYearsExperience");
            if (expNode.isNull()) {
                jobPosting.setMinYearsExperience(null);
            } else {
                jobPosting.setMinYearsExperience(expNode.asInt());
            }
        }

        if (patchNode.has("seniorityLevel")) {
            com.fasterxml.jackson.databind.JsonNode levelNode = patchNode.get("seniorityLevel");
            if (levelNode.isNull()) {
                jobPosting.setSeniorityLevel(null);
            } else {
                try {
                    jobPosting.setSeniorityLevel(SeniorityLevel.valueOf(levelNode.asText().toUpperCase()));
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid seniorityLevel value");
                }
            }
        }

        if (patchNode.has("status")) {
            com.fasterxml.jackson.databind.JsonNode statusNode = patchNode.get("status");
            if (statusNode.isNull()) {
                throw new IllegalArgumentException("Status cannot be null");
            } else {
                try {
                    jobPosting.setStatus(JobPostingStatus.valueOf(statusNode.asText().toUpperCase()));
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid status value");
                }
            }
        }

        jobPosting.setUpdatedAt(java.time.OffsetDateTime.now());
        JobPosting saved = jobPostingRepository.saveAndFlush(jobPosting);

        return new JobPostingResponse(
                saved.getId(),
                saved.getUser().getId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getRequiredSkills(),
                saved.getNiceToHaveSkills(),
                saved.getMinYearsExperience(),
                saved.getSeniorityLevel(),
                saved.getStatus(),
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );
    }
}
