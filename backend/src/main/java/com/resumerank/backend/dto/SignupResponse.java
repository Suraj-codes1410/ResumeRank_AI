package com.resumerank.backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SignupResponse(
    UUID userId,
    String email,
    OffsetDateTime createdAt
) {}
