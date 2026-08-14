package com.mthind.tentflow.model;

import java.util.Objects;

//immutable reservation snapshot returned to callers
public record ReservationView(
        long id,
        Customer customer,
        TentType tentType,
        int quantity,
        TimeRange eventTime,
        TimeRange reservedTime,
        String location,
        ReservationStatus status
) {

    public ReservationView {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "Reservation ID must be positive."
            );
        }

        Objects.requireNonNull(
                customer,
                "Customer cannot be null."
        );

        Objects.requireNonNull(
                tentType,
                "Tent type cannot be null."
        );

        Objects.requireNonNull(
                eventTime,
                "Event time cannot be null."
        );

        Objects.requireNonNull(
                reservedTime,
                "Reserved time cannot be null."
        );

        Objects.requireNonNull(
                location,
                "Location cannot be null."
        );

        Objects.requireNonNull(
                status,
                "Status cannot be null."
        );
    }
}
