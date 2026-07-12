package com.resumerank.backend.dto;

import com.resumerank.backend.entity.PipelineStatus;
import com.resumerank.backend.entity.ResumeStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public class CandidateResponse {
    private UUID id;
    private UUID jobPostingId;
    private String name;
    private String email;
    private String resumeFileUrl;
    private ResumeStatus resumeStatus;
    private String parseError;
    private PipelineStatus pipelineStatus;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public CandidateResponse() {
    }

    public CandidateResponse(UUID id, UUID jobPostingId, String name, String email, String resumeFileUrl,
                             ResumeStatus resumeStatus, String parseError, PipelineStatus pipelineStatus,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.jobPostingId = jobPostingId;
        this.name = name;
        this.email = email;
        this.resumeFileUrl = resumeFileUrl;
        this.resumeStatus = resumeStatus;
        this.parseError = parseError;
        this.pipelineStatus = pipelineStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getJobPostingId() {
        return jobPostingId;
    }

    public void setJobPostingId(UUID jobPostingId) {
        this.jobPostingId = jobPostingId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getResumeFileUrl() {
        return resumeFileUrl;
    }

    public void setResumeFileUrl(String resumeFileUrl) {
        this.resumeFileUrl = resumeFileUrl;
    }

    public ResumeStatus getResumeStatus() {
        return resumeStatus;
    }

    public void setResumeStatus(ResumeStatus resumeStatus) {
        this.resumeStatus = resumeStatus;
    }

    public String getParseError() {
        return parseError;
    }

    public void setParseError(String parseError) {
        this.parseError = parseError;
    }

    public PipelineStatus getPipelineStatus() {
        return pipelineStatus;
    }

    public void setPipelineStatus(PipelineStatus pipelineStatus) {
        this.pipelineStatus = pipelineStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
