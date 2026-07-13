package com.resumerank.backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class CandidateStatusLogResponse {
    private UUID id;
    private String fromStatus;
    private String toStatus;
    private String changedByEmail;
    private OffsetDateTime createdAt;

    public CandidateStatusLogResponse() {
    }

    public CandidateStatusLogResponse(UUID id, String fromStatus, String toStatus, String changedByEmail, OffsetDateTime createdAt) {
        this.id = id;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByEmail = changedByEmail;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getChangedByEmail() {
        return changedByEmail;
    }

    public void setChangedByEmail(String changedByEmail) {
        this.changedByEmail = changedByEmail;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
