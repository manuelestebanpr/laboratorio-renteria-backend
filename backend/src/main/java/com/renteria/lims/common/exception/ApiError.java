package com.renteria.lims.common.exception;

import java.time.Instant;

public class ApiError {
    
    private final String error;
    private final String message;
    private final Instant timestamp;
    private final String path;
    
    public ApiError(String error, String message, String path) {
        this.error = error;
        this.message = message;
        this.timestamp = Instant.now();
        this.path = path;
    }
    
    public String getError() {
        return error;
    }
    
    public String getMessage() {
        return message;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public String getPath() {
        return path;
    }
}
