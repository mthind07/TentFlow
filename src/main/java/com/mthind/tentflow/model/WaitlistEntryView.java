package com.mthind.tentflow.model;

import java.time.LocalDateTime;
import java.util.Objects;

//immutable waitlist-entry snapshot returned to callers
public record WaitlistEntryView(
        long id,
        ReservationView reservation,
        LocalDateTime joinedAt
) {

    public WaitlistEntryView {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "Waitlist entry ID must be positive."
            );
        }

        Objects.requireNonNull(
                reservation,
                "Reservation cannot be null."
        );

        Objects.requireNonNull(
                joinedAt,
                "Join time cannot be null."
        );
    }
}