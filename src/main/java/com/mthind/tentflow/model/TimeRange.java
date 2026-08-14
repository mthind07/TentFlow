package com.mthind.tentflow.model;

import java.time.LocalDateTime;

//represents a validated start and end date/time
public final class TimeRange {

    private final LocalDateTime start;
    private final LocalDateTime end;

    public TimeRange(
            LocalDateTime start,
            LocalDateTime end
    ) {
        if (start == null || end == null) {
            throw new IllegalArgumentException(
                    "A time range needs both a start and an end."
            );
        }

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "The end time must be after the start time."
            );
        }

        this.start = start;
        this.end = end;
    }

    //a range ending when another begins does not overlap
    public boolean overlaps(TimeRange other) {
        if (other == null) {
            throw new IllegalArgumentException(
                    "The other time range cannot be null."
            );
        }

        return start.isBefore(other.end)
                && end.isAfter(other.start);
    }

    //checks whether this range fully contains another
    public boolean contains(TimeRange other) {
        if (other == null) {
            throw new IllegalArgumentException(
                    "The other time range cannot be null."
            );
        }

        boolean beginsBeforeOrAtOther =
                !other.start.isBefore(start);

        boolean endsAfterOrAtOther =
                !other.end.isAfter(end);

        return beginsBeforeOrAtOther && endsAfterOrAtOther;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return start + " to " + end;
    }
}