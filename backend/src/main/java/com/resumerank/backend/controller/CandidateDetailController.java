package com.resumerank.backend.controller;

import com.resumerank.backend.dto.CandidateResponse;
import com.resumerank.backend.service.CandidateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/candidates")
public class CandidateDetailController {

    @Autowired
    private CandidateService candidateService;

    @GetMapping("/{candidateId}")
    public ResponseEntity<CandidateResponse> getCandidateDetail(
            @PathVariable("candidateId") UUID candidateId,
            HttpServletRequest servletRequest) {
        UUID authenticatedUserId = (UUID) servletRequest.getAttribute("authenticatedUserId");
        CandidateResponse response = candidateService.getCandidateDetail(authenticatedUserId, candidateId);
        return ResponseEntity.ok(response);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{candidateId}/status")
    public ResponseEntity<CandidateResponse> updateCandidateStatus(
            @PathVariable("candidateId") UUID candidateId,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.resumerank.backend.dto.CandidateStatusUpdateRequest request,
            HttpServletRequest servletRequest) {
        UUID authenticatedUserId = (UUID) servletRequest.getAttribute("authenticatedUserId");
        CandidateResponse response = candidateService.updateCandidateStatus(authenticatedUserId, candidateId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{candidateId}/status-log")
    public ResponseEntity<java.util.List<com.resumerank.backend.dto.CandidateStatusLogResponse>> getCandidateStatusLog(
            @PathVariable("candidateId") UUID candidateId,
            HttpServletRequest servletRequest) {
        UUID authenticatedUserId = (UUID) servletRequest.getAttribute("authenticatedUserId");
        java.util.List<com.resumerank.backend.dto.CandidateStatusLogResponse> response =
                candidateService.getCandidateStatusLog(authenticatedUserId, candidateId);
        return ResponseEntity.ok(response);
    }
}
