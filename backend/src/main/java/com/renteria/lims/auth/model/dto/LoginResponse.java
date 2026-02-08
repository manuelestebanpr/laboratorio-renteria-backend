package com.renteria.lims.auth.model.dto;

import java.util.Set;
import java.util.UUID;

public record LoginResponse(
    String accessToken,
    long expiresIn,
    boolean forcePasswordChange,
    UserInfo user
) {
    public record UserInfo(
        UUID id,
        String email,
        String role,
        String fullName
    ) {}
}
