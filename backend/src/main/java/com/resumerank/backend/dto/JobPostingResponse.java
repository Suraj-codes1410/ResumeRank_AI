package com.resumerank.backend.dto;

import com.resumerank.backend.entity.SeniorityLevel;
import com.resumerank.backend.entity.JobPostingStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record JobPostingResponse(
    UUID id,
    UUID userId,
    String title,
    String description,
    String[] requiredSkills,
    String[] niceToHaveSkills,
    Integer minYearsExperience,
    SeniorityLevel seniorityLevel,
    JobPostingStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
