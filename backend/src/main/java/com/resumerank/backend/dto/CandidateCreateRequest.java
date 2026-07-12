package com.resumerank.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class CandidateCreateRequest {

    @NotBlank(message = "Resume file URL is required")
    private String resumeFileUrl;

    public CandidateCreateRequest() {
    }

    public CandidateCreateRequest(String resumeFileUrl) {
        this.resumeFileUrl = resumeFileUrl;
    }

    public String getResumeFileUrl() {
        return resumeFileUrl;
    }

    public void setResumeFileUrl(String resumeFileUrl) {
        this.resumeFileUrl = resumeFileUrl;
    }
}
