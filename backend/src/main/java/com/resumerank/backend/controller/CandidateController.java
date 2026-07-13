package com.resumerank.backend.controller;

import com.resumerank.backend.dto.CandidateCreateRequest;
import com.resumerank.backend.dto.CandidateResponse;
import com.resumerank.backend.service.CandidateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.resumerank.backend.dto.CandidateListResponse;
import java.util.UUID;

@RestController
@RequestMapping("/api/job-postings/{jobPostingId}/candidates")
public class CandidateController {

    @Autowired
    private CandidateService candidateService;

    @PostMapping
    public ResponseEntity<CandidateResponse> createCandidate(
            @PathVariable("jobPostingId") UUID jobPostingId,
            @Valid @RequestBody CandidateCreateRequest request,
            HttpServletRequest servletRequest) {
        UUID authenticatedUserId = (UUID) servletRequest.getAttribute("authenticatedUserId");
        CandidateResponse response = candidateService.createCandidate(authenticatedUserId, jobPostingId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<CandidateListResponse> getCandidates(
            @PathVariable("jobPostingId") UUID jobPostingId,
            @org.springframework.web.bind.annotation.RequestParam(value = "sort", defaultValue = "score_desc") String sort,
            @org.springframework.web.bind.annotation.RequestParam(value = "minScore", required = false) Integer minScore,
            @org.springframework.web.bind.annotation.RequestParam(value = "skill", required = false) String skill,
            @org.springframework.web.bind.annotation.RequestParam(value = "search", required = false) String search,
            @org.springframework.web.bind.annotation.RequestParam(value = "resumeStatus", required = false) String resumeStatus,
            @org.springframework.web.bind.annotation.RequestParam(value = "cursor", required = false) String cursor,
            @org.springframework.web.bind.annotation.RequestParam(value = "limit", defaultValue = "25") Integer limit,
            HttpServletRequest servletRequest) {
        UUID authenticatedUserId = (UUID) servletRequest.getAttribute("authenticatedUserId");
        CandidateListResponse response = candidateService.getCandidatesList(
                authenticatedUserId, jobPostingId, sort, minScore, skill, search, resumeStatus, cursor, limit
        );
        return ResponseEntity.ok(response);
    }
}
