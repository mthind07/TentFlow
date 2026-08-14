package com.mthind.tentflow.model;

import com.mthind.tentflow.exception.InvalidReservationStateException;

//Mutable reservation kept privately inside TentBookingService
public final class Reservation {

    private final long id;
    private final Customer customer;
    private final TentType tentType;
    private final int quantity;
    private final TimeRange eventTime;
    private final TimeRange reservedTime;
    private final String location;

    private ReservationStatus status;

    public Reservation(
            long id,
            Customer customer,
            TentType tentType,
            int quantity,
            TimeRange eventTime,
            TimeRange reservedTime,
            String location,
            ReservationStatus initialStatus
    ) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "Reservation ID must be positive."
            );
        }

        if (customer == null || tentType == null) {
            throw new IllegalArgumentException(
                    "A reservation needs a customer and tent type."
            );
        }

        if (quantity <= 0
                || quantity > tentType.getTotalQuantity()) {

            throw new IllegalArgumentException(
                    "Quantity must be between 1 and "
                            + tentType.getTotalQuantity()
                            + "."
            );
        }

        if (eventTime == null || reservedTime == null) {
            throw new IllegalArgumentException(
                    "Event and reserved time ranges are required."
            );
        }

        if (!reservedTime.contains(eventTime)) {
            throw new IllegalArgumentException(
                    "The setup/pickup window must contain "
                            + "the entire event."
            );
        }

        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException(
                    "Event location cannot be blank."
            );
        }

        if (initialStatus != ReservationStatus.PENDING
                && initialStatus != ReservationStatus.WAITLISTED) {

            throw new IllegalArgumentException(
                    "A new reservation must begin as "
                            + "PENDING or WAITLISTED."
            );
        }

        this.id = id;
        this.customer = customer;
        this.tentType = tentType;
        this.quantity = quantity;
        this.eventTime = eventTime;
        this.reservedTime = reservedTime;
        this.location = location.trim();
        this.status = initialStatus;
    }

    public void confirm() {
        requireStatus(
                ReservationStatus.PENDING,
                "Only a pending reservation can be confirmed."
        );

        status = ReservationStatus.CONFIRMED;
    }

    public void promoteFromWaitlist() {
        requireStatus(
                ReservationStatus.WAITLISTED,
                "Only a waitlisted reservation can be promoted."
        );

        status = ReservationStatus.PENDING;
    }

    public void reject() {
        if (status != ReservationStatus.PENDING
                && status != ReservationStatus.WAITLISTED) {

            throw new InvalidReservationStateException(
                    "Only a pending or waitlisted "
                            + "reservation can be rejected."
            );
        }

        status = ReservationStatus.REJECTED;
    }

    public void cancel() {
        if (status != ReservationStatus.PENDING
                && status != ReservationStatus.CONFIRMED
                && status != ReservationStatus.WAITLISTED) {

            throw new InvalidReservationStateException(
                    "Only a pending, confirmed, or waitlisted "
                            + "reservation can be cancelled."
            );
        }

        status = ReservationStatus.CANCELLED;
    }

    public void complete() {
        requireStatus(
                ReservationStatus.CONFIRMED,
                "Only a confirmed reservation can be completed."
        );

        status = ReservationStatus.COMPLETED;
    }

    private void requireStatus(
            ReservationStatus expected,
            String errorMessage
    ) {
        if (status != expected) {
            throw new InvalidReservationStateException(
                    errorMessage + " Current status: " + status
            );
        }
    }

    public boolean blocksInventory() {
        return status.blocksInventory();
    }

    public long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public TentType getTentType() {
        return tentType;
    }

    public int getQuantity() {
        return quantity;
    }

    public TimeRange getEventTime() {
        return eventTime;
    }

    public TimeRange getReservedTime() {
        return reservedTime;
    }

    public String getLocation() {
        return location;
    }

    public ReservationStatus getStatus() {
        return status;
    }
}