package com.mthind.tentflow.api.dto;

import java.util.List;

public record AuditPageResponse(
        List<AuditEventResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public AuditPageResponse {
        //audit pages are snapshots; callers cannot mutate their contents
        content = List.copyOf(content);
    }
}