package com.mthind.tentflow.api.dto;

import java.time.LocalDateTime;

//maintenance-block JSON returned by the API
public record MaintenanceBlockResponse(
        long id,
        long tentId,
        String tentSize,
        int quantityUnavailable,
        LocalDateTime from,
        LocalDateTime until,
        String reason
) {
}