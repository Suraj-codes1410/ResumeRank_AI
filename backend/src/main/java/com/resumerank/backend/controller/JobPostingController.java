package com.resumerank.backend.controller;

import com.resumerank.backend.dto.JobPostingCreateRequest;
import com.resumerank.backend.dto.JobPostingResponse;
import com.resumerank.backend.service.JobPostingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/job-postings")
public class JobPostingController {

    private final JobPostingService jobPostingService;

    public JobPostingController(JobPostingService jobPostingService) {
        this.jobPostingService = jobPostingService;
    }

    @PostMapping
    public ResponseEntity<JobPostingResponse> createJobPosting(
            @Valid @RequestBody JobPostingCreateRequest request,
            HttpServletRequest servletRequest) {
        UUID authenticatedUserId = (UUID) servletRequest.getAttribute("authenticatedUserId");
        JobPostingResponse response = jobPostingService.createJobPosting(authenticatedUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<com.resumerank.backend.dto.JobPostingListResponse> listJobPostings(
            @RequestParam(name = "status", required = false) com.resumerank.backend.entity.JobPostingStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            HttpServletRequest servletRequest) {
        UUID authenticatedUserId = (UUID) servletRequest.getAttribute("authenticatedUserId");
        com.resumerank.backend.dto.JobPostingListResponse response = jobPostingService.listJobPostings(
                authenticatedUserId, status, page, size
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobPostingResponse> getJobPosting(
            @PathVariable("id") UUID id,
            HttpServletRequest servletRequest) {
        UUID authenticatedUserId = (UUID) servletRequest.getAttribute("authenticatedUserId");
        JobPostingResponse response = jobPostingService.getJobPosting(authenticatedUserId, id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<JobPostingResponse> updateJobPosting(
            @PathVariable("id") UUID id,
            @RequestBody com.fasterxml.jackson.databind.JsonNode patchNode,
            HttpServletRequest servletRequest) {
        UUID authenticatedUserId = (UUID) servletRequest.getAttribute("authenticatedUserId");
        JobPostingResponse response = jobPostingService.updateJobPosting(authenticatedUserId, id, patchNode);
        return ResponseEntity.ok(response);
    }
}
