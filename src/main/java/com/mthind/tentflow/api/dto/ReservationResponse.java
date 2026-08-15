package com.mthind.tentflow.api.dto;

import com.mthind.tentflow.model.ReservationStatus;

//reservation JSON with relationship IDs and useful display labels
public record ReservationResponse(
        long id,
        long customerId,
        String customerName,
        long tentId,
        String tentSize,
        int quantity,
        TimeRangeResponse eventTime,
        TimeRangeResponse reservedTime,
        String location,
        ReservationStatus status
) {
}