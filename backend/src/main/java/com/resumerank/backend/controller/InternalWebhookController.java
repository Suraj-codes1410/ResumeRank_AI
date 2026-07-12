package com.resumerank.backend.controller;

import com.resumerank.backend.dto.AiWebhookPayload;
import com.resumerank.backend.service.CandidateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/ai-webhook")
public class InternalWebhookController {

    @Autowired
    private CandidateService candidateService;

    @Value("${INTERNAL_SERVICE_TOKEN:5ec834ec8d0b81d070cde05c99231c6bab517c11f510cf5139353204841b42b8}")
    private String internalServiceToken;

    @PostMapping
    public ResponseEntity<Void> receiveWebhook(
            @RequestHeader(value = "X-Internal-Token", required = false) String incomingToken,
            @RequestBody(required = false) AiWebhookPayload payload) {
        
        if (incomingToken == null || incomingToken.isEmpty() || !incomingToken.equals(internalServiceToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (payload == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        candidateService.handleAiWebhook(payload);
        return ResponseEntity.ok().build();
    }
}
