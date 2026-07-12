package com.resumerank.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class CandidateCreateRequest {

    @NotBlank(message = "Resume file URL is required")
    private String resumeFileUrl;

    private String resumeHash;

    public CandidateCreateRequest() {
    }

    public CandidateCreateRequest(String resumeFileUrl) {
        this.resumeFileUrl = resumeFileUrl;
    }

    public CandidateCreateRequest(String resumeFileUrl, String resumeHash) {
        this.resumeFileUrl = resumeFileUrl;
        this.resumeHash = resumeHash;
    }

    public String getResumeFileUrl() {
        return resumeFileUrl;
    }

    public void setResumeFileUrl(String resumeFileUrl) {
        this.resumeFileUrl = resumeFileUrl;
    }

    public String getResumeHash() {
        return resumeHash;
    }

    public void setResumeHash(String resumeHash) {
        this.resumeHash = resumeHash;
    }
}
