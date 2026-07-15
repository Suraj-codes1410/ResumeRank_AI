package com.resumerank.backend.dto;

import java.util.List;

public class BulkStatusUpdateResponse {
    private List<String> updated;
    private List<String> skipped;

    public BulkStatusUpdateResponse() {
    }

    public BulkStatusUpdateResponse(List<String> updated, List<String> skipped) {
        this.updated = updated;
        this.skipped = skipped;
    }

    public List<String> getUpdated() {
        return updated;
    }

    public void setUpdated(List<String> updated) {
        this.updated = updated;
    }

    public List<String> getSkipped() {
        return skipped;
    }

    public void setSkipped(List<String> skipped) {
        this.skipped = skipped;
    }
}
