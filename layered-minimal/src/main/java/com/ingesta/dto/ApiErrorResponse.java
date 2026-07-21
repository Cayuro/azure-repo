package com.ingesta.dto;

import java.util.List;

public record ApiErrorResponse(
        int status,
        String error,
        String message,
        List<String> details
) {
    public static ApiErrorResponse of(int status, String error, String message, List<String> details) {
        return new ApiErrorResponse(status, error, message, details);
    }

    public static ApiErrorResponse of(int status, String error, String message) {
        return new ApiErrorResponse(status, error, message, List.of());
    }
}
