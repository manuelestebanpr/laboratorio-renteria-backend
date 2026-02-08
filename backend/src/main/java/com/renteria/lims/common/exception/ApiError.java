package com.renteria.lims.common.exception;

import java.time.Instant;

public record ApiError(
    String error,
    String message,
    Instant timestamp,
    String path
) {
    public ApiError(String error, String message, String path) {
        this(error, message, Instant.now(), path);
    }
}
