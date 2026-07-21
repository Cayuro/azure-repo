package com.ingesta.dto;

import java.util.List;

public class ApiErrorResponse {
    private final int status;
    private final String error;
    private final String message;
    private final List<String> details;

    public ApiErrorResponse(int status, String error, String message, List<String> details) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.details = details;
    }

    public static ApiErrorResponse of(int status, String error, String message, List<String> details) {
        return new ApiErrorResponse(status, error, message, details);
    }

    public static ApiErrorResponse of(int status, String error, String message) {
        return new ApiErrorResponse(status, error, message, List.of());
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getDetails() {
        return details;
    }
}
