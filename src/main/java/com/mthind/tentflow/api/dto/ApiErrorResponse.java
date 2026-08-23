package com.mthind.tentflow.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

//stable JSON envelope used for every API error
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        UUID requestId,
        Map<String, String> fieldErrors
) {
    public ApiErrorResponse {
        //copy mutable caller input so an error response cannot change after it has crossed the API boundary
        fieldErrors = fieldErrors == null
                ? Map.of()
                : Map.copyOf(fieldErrors);
    }
}