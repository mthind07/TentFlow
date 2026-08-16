package com.mthind.tentflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private ReservationEntity reservation;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected WaitlistEntryEntity() {
        //required by JPA
    }

    public WaitlistEntryEntity(
            ReservationEntity reservation,
            Instant joinedAt
    ) {
        this.reservation = reservation;
        this.joinedAt = joinedAt;
    }

    public Long getId() {
        return id;
    }

    public ReservationEntity getReservation() {
        return reservation;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}