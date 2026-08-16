package com.mthind.tentflow.persistence.entity;

import com.mthind.tentflow.exception.InvalidReservationStateException;
import com.mthind.tentflow.model.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.EnumSet;

@Entity
@Table(name = "reservations")
public class ReservationEntity {

    private static final EnumSet<ReservationStatus> REJECTABLE_STATUSES =
            EnumSet.of(
                    ReservationStatus.PENDING,
                    ReservationStatus.WAITLISTED
            );

    private static final EnumSet<ReservationStatus> CANCELLABLE_STATUSES =
            EnumSet.of(
                    ReservationStatus.PENDING,
                    ReservationStatus.CONFIRMED,
                    ReservationStatus.WAITLISTED
            );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tent_type_id", nullable = false)
    private TentTypeEntity tentType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "event_start", nullable = false)
    private LocalDateTime eventStart;

    @Column(name = "event_end", nullable = false)
    private LocalDateTime eventEnd;

    @Column(name = "reserved_from", nullable = false)
    private LocalDateTime reservedFrom;

    @Column(name = "reserved_until", nullable = false)
    private LocalDateTime reservedUntil;

    @Column(nullable = false, length = 250)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    protected ReservationEntity() {
        //required by JPA
    }

    public ReservationEntity(
            CustomerEntity customer,
            TentTypeEntity tentType,
            int quantity,
            LocalDateTime eventStart,
            LocalDateTime eventEnd,
            LocalDateTime reservedFrom,
            LocalDateTime reservedUntil,
            String location,
            ReservationStatus status
    ) {
        this.customer = customer;
        this.tentType = tentType;
        this.quantity = quantity;
        this.eventStart = eventStart;
        this.eventEnd = eventEnd;
        this.reservedFrom = reservedFrom;
        this.reservedUntil = reservedUntil;
        this.location = location;
        this.status = status;
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
        if (!REJECTABLE_STATUSES.contains(status)) {
            throw new InvalidReservationStateException(
                    "Only a pending or waitlisted reservation can be rejected."
            );
        }
        status = ReservationStatus.REJECTED;
    }

    public void cancel() {
        if (!CANCELLABLE_STATUSES.contains(status)) {
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
            String message
    ) {
        if (status != expected) {
            throw new InvalidReservationStateException(
                    message + " Current status: " + status
            );
        }
    }

    public Long getId() {
        return id;
    }

    public CustomerEntity getCustomer() {
        return customer;
    }

    public TentTypeEntity getTentType() {
        return tentType;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDateTime getEventStart() {
        return eventStart;
    }

    public LocalDateTime getEventEnd() {
        return eventEnd;
    }

    public LocalDateTime getReservedFrom() {
        return reservedFrom;
    }

    public LocalDateTime getReservedUntil() {
        return reservedUntil;
    }

    public String getLocation() {
        return location;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public boolean blocksInventory() {
        return status.blocksInventory();
    }
}