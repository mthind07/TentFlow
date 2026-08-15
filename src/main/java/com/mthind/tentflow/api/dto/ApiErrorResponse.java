package com.mthind.tentflow.api.dto;

import java.time.Instant;
import java.util.Map;

//stable JSON envelope used for every API error
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}