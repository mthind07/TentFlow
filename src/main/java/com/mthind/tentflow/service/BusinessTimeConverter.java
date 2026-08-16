package com.mthind.tentflow.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class BusinessTimeConverter {

    public static final ZoneId OPERATING_ZONE =
            ZoneId.of("America/Toronto");

    public LocalDateTime requireUnambiguous(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            throw new IllegalArgumentException(
                    "Business date/time cannot be null."
            );
        }

        if (localDateTime.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException(
                    localDateTime
                            + " is more precise than PostgreSQL's "
                            + "supported six fractional-second digits."
            );
        }

        List<ZoneOffset> offsets = OPERATING_ZONE
                .getRules()
                .getValidOffsets(localDateTime);

        if (offsets.size() != 1) {
            throw new IllegalArgumentException(
                    localDateTime
                            + " is not an unambiguous time in "
                            + OPERATING_ZONE
                            + ". Choose a time outside the daylight-saving "
                            + "gap or overlap."
            );
        }

        return localDateTime;
    }

    public LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException(
                    "Stored instant cannot be null."
            );
        }

        return LocalDateTime.ofInstant(instant, OPERATING_ZONE);
    }
}
