package com.mthind.tentflow.api.dto;

import java.time.LocalDateTime;

//start/end pair used inside reservation responses
public record TimeRangeResponse(
        LocalDateTime start,
        LocalDateTime end
) {
}