package com.mthind.tentflow.api;

import com.mthind.tentflow.api.dto.CustomerResponse;
import com.mthind.tentflow.api.dto.MaintenanceBlockResponse;
import com.mthind.tentflow.api.dto.ReservationResponse;
import com.mthind.tentflow.api.dto.TentResponse;
import com.mthind.tentflow.api.dto.TimeRangeResponse;
import com.mthind.tentflow.api.dto.WaitlistEntryResponse;
import com.mthind.tentflow.model.Customer;
import com.mthind.tentflow.model.MaintenanceBlock;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.model.TentType;
import com.mthind.tentflow.model.TimeRange;
import com.mthind.tentflow.model.WaitlistEntryView;
import org.springframework.stereotype.Component;

//Keeps domain objects out of the public JSON contract
@Component
public class ApiMapper {

    public CustomerResponse toCustomerResponse(
            Customer customer
    ) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getPhone()
        );
    }

    public TentResponse toTentResponse(
            TentType tentType
    ) {
        return new TentResponse(
                tentType.getId(),
                tentType.getWidthFeet(),
                tentType.getLengthFeet(),
                tentType.getSizeLabel(),
                tentType.getTotalQuantity()
        );
    }

    public ReservationResponse toReservationResponse(
            ReservationView reservation
    ) {
        return new ReservationResponse(
                reservation.id(),
                reservation.customer().getId(),
                reservation.customer().getFullName(),
                reservation.tentType().getId(),
                reservation.tentType().getSizeLabel(),
                reservation.quantity(),
                toTimeRangeResponse(
                        reservation.eventTime()
                ),
                toTimeRangeResponse(
                        reservation.reservedTime()
                ),
                reservation.location(),
                reservation.status()
        );
    }

    public WaitlistEntryResponse toWaitlistEntryResponse(
            WaitlistEntryView entry
    ) {
        ReservationView reservation =
                entry.reservation();

        return new WaitlistEntryResponse(
                entry.id(),
                reservation.id(),
                reservation.customer().getId(),
                reservation.customer().getFullName(),
                reservation.quantity(),
                entry.joinedAt()
        );
    }

    public MaintenanceBlockResponse toMaintenanceBlockResponse(
            MaintenanceBlock block
    ) {
        return new MaintenanceBlockResponse(
                block.getId(),
                block.getTentType().getId(),
                block.getTentType().getSizeLabel(),
                block.getQuantityUnavailable(),
                block.getTimeRange().getStart(),
                block.getTimeRange().getEnd(),
                block.getReason()
        );
    }

    private TimeRangeResponse toTimeRangeResponse(
            TimeRange timeRange
    ) {
        return new TimeRangeResponse(
                timeRange.getStart(),
                timeRange.getEnd()
        );
    }
}
