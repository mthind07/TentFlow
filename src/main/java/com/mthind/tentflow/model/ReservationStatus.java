package com.mthind.tentflow.model;

import java.util.EnumSet;

//lists every possible reservation status
public enum ReservationStatus {

    PENDING,
    CONFIRMED,
    WAITLISTED,
    REJECTED,
    CANCELLED,
    COMPLETED;

    private static final EnumSet<ReservationStatus>
            INVENTORY_BLOCKING_STATUSES =
            EnumSet.of(PENDING, CONFIRMED);

    //pending and confirmed reservations hold inventory.
    public boolean blocksInventory() {
        return INVENTORY_BLOCKING_STATUSES.contains(this);
    }
}


