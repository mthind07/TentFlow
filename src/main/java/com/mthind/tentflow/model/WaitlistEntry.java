package com.mthind.tentflow.model;

import java.time.LocalDateTime;

//internal queue entry for a waitlisted reservation
public final class WaitlistEntry
        implements Comparable<WaitlistEntry> {

    private final long id;
    private final Reservation reservation;
    private final LocalDateTime joinedAt;

    public WaitlistEntry(
            long id,
            Reservation reservation,
            LocalDateTime joinedAt
    ) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "Waitlist entry ID must be positive."
            );
        }

        if (reservation == null || joinedAt == null) {
            throw new IllegalArgumentException(
                    "Reservation and join time are required."
            );
        }

        if (reservation.getStatus()
                != ReservationStatus.WAITLISTED) {

            throw new IllegalArgumentException(
                    "Only a waitlisted reservation "
                            + "can create a waitlist entry."
            );
        }

        this.id = id;
        this.reservation = reservation;
        this.joinedAt = joinedAt;
    }

    @Override
    public int compareTo(WaitlistEntry other) {
        int timeComparison =
                joinedAt.compareTo(other.joinedAt);

        if (timeComparison != 0) {
            return timeComparison;
        }

        return Long.compare(id, other.id);
    }

    public long getId() {
        return id;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
