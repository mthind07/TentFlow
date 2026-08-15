package com.mthind.tentflow.api.dto;

import java.time.LocalDateTime;

//one ordered waitlist row
public record WaitlistEntryResponse(
        long id,
        long reservationId,
        long customerId,
        String customerName,
        int quantity,
        LocalDateTime joinedAt
) {
}