package com.resumerank.backend.controller;

import com.resumerank.backend.dto.JobPostingCreateRequest;
import com.resumerank.backend.dto.JobPostingResponse;
import com.resumerank.backend.service.JobPostingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
