package com.resumerank.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class BulkStatusUpdateRequest {

    @NotEmpty(message = "Candidate IDs cannot be empty")
    private List<String> candidateIds;

    @NotBlank(message = "Status is required")
    private String status;

    public BulkStatusUpdateRequest() {
    }

    public List<String> getCandidateIds() {
        return candidateIds;
    }

    public void setCandidateIds(List<String> candidateIds) {
        this.candidateIds = candidateIds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
