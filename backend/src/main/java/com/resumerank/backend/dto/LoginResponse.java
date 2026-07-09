package com.resumerank.backend.dto;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    boolean emailVerified
) {}

