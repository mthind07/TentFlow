package com.mthind.tentflow.api.dto;

import java.util.List;

public record AuditPageResponse(
        List<AuditEventResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
