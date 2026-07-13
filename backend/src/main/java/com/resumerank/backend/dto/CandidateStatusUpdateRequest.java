package com.resumerank.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class CandidateStatusUpdateRequest {

    @NotBlank(message = "Status is required")
    private String status;

    public CandidateStatusUpdateRequest() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
