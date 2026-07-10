package com.resumerank.backend.dto;

import com.resumerank.backend.entity.SeniorityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobPostingCreateRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 250, message = "Title must not exceed 250 characters")
    String title,

    @NotBlank(message = "Description is required")
    @Size(max = 10000, message = "Description must not exceed 10000 characters")
    String description,

    String[] requiredSkills,
    String[] niceToHaveSkills,
    Integer minYearsExperience,
    SeniorityLevel seniorityLevel
) {}
