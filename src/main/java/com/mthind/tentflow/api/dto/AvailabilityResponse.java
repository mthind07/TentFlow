package com.mthind.tentflow.api.dto;

import java.time.LocalDateTime;

//availability for one tent type over one setup-through-pickup window
public record AvailabilityResponse(
        long tentId,
        String tentSize,
        LocalDateTime from,
        LocalDateTime until,
        int availableQuantity,
        int totalQuantity
) {
}
