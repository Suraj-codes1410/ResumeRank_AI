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
}
