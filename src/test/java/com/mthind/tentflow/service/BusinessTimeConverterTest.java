package com.mthind.tentflow.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessTimeConverterTest {

    private final BusinessTimeConverter converter =
            new BusinessTimeConverter();

    @Test
    void acceptsOrdinaryTorontoWallTimeAndConvertsOperationalInstant() {
        LocalDateTime ordinary =
                LocalDateTime.of(2027, 6, 12, 10, 30);

        assertEquals(ordinary, converter.requireUnambiguous(ordinary));
        assertEquals(
                ordinary,
                converter.toLocalDateTime(
                        Instant.parse("2027-06-12T14:30:00Z")
                )
        );
    }

    @Test
    void rejectsTorontoSpringDaylightSavingGap() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> converter.requireUnambiguous(
                        LocalDateTime.of(2027, 3, 14, 2, 30)
                )
        );

        assertTrue(exception.getMessage().contains("daylight-saving gap"));
    }

    @Test
    void rejectsTorontoAutumnDaylightSavingOverlap() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> converter.requireUnambiguous(
                        LocalDateTime.of(2027, 11, 7, 1, 30)
                )
        );

        assertTrue(exception.getMessage().contains("daylight-saving gap"));
    }

    @Test
    void rejectsPrecisionFinerThanPostgresMicroseconds() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> converter.requireUnambiguous(
                        LocalDateTime.parse(
                                "2027-06-12T10:30:00.123456789"
                        )
                )
        );

        assertTrue(exception.getMessage().contains(
                "supported six fractional-second digits"
        ));
    }
}