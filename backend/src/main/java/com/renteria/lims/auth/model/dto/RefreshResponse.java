package com.renteria.lims.auth.model.dto;

public record RefreshResponse(
    String accessToken,
    long expiresIn
) {}
