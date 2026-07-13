package com.resumerank.backend.dto;

import java.util.List;

public class CandidateListResponse {
    private List<CandidateResponse> items;
    private String nextCursor;

    public CandidateListResponse() {
    }

    public CandidateListResponse(List<CandidateResponse> items, String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }

    public List<CandidateResponse> getItems() {
        return items;
    }

    public void setItems(List<CandidateResponse> items) {
        this.items = items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}
