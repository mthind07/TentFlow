package com.mthind.tentflow.service;

import com.mthind.tentflow.model.Customer;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.model.TentType;
import com.mthind.tentflow.model.TimeRange;
import com.mthind.tentflow.persistence.entity.ReservationEntity;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class PersistenceViewMapper {

    public ReservationView toReservationView(ReservationEntity entity) {
        return new ReservationView(
                Objects.requireNonNull(entity.getId()),
                new Customer(
                        Objects.requireNonNull(entity.getCustomer().getId()),
                        entity.getCustomer().getFullName(),
                        entity.getCustomer().getEmail(),
                        entity.getCustomer().getPhone()
                ),
                new TentType(
                        Objects.requireNonNull(entity.getTentType().getId()),
                        entity.getTentType().getWidthFeet(),
                        entity.getTentType().getLengthFeet(),
                        entity.getTentType().getTotalQuantity()
                ),
                entity.getQuantity(),
                new TimeRange(entity.getEventStart(), entity.getEventEnd()),
                new TimeRange(
                        entity.getReservedFrom(),
                        entity.getReservedUntil()
                ),
                entity.getLocation(),
                entity.getStatus()
        );
    }
}
