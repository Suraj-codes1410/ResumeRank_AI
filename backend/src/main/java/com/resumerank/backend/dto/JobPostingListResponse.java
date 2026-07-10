package com.resumerank.backend.dto;

import java.util.List;

public record JobPostingListResponse(
    List<JobPostingResponse> items,
    int page,
    int size,
    long totalItems
) {}
