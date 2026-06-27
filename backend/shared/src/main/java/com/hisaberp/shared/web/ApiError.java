package com.hisaberp.shared.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldError> fieldErrors,
        Map<String, Object> details
) {
    public record FieldError(String field, String code, String message) {}
}
