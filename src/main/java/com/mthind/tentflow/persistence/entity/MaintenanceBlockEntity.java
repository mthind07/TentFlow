package com.mthind.tentflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_blocks")
public class MaintenanceBlockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tent_type_id", nullable = false)
    private TentTypeEntity tentType;

    @Column(name = "quantity_unavailable", nullable = false)
    private int quantityUnavailable;

    @Column(name = "blocked_from", nullable = false)
    private LocalDateTime blockedFrom;

    @Column(name = "blocked_until", nullable = false)
    private LocalDateTime blockedUntil;

    @Column(nullable = false, length = 250)
    private String reason;

    protected MaintenanceBlockEntity() {
        //required by JPA
    }

    public MaintenanceBlockEntity(
            TentTypeEntity tentType,
            int quantityUnavailable,
            LocalDateTime blockedFrom,
            LocalDateTime blockedUntil,
            String reason
    ) {
        this.tentType = tentType;
        this.quantityUnavailable = quantityUnavailable;
        this.blockedFrom = blockedFrom;
        this.blockedUntil = blockedUntil;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public TentTypeEntity getTentType() {
        return tentType;
    }

    public int getQuantityUnavailable() {
        return quantityUnavailable;
    }

    public LocalDateTime getBlockedFrom() {
        return blockedFrom;
    }

    public LocalDateTime getBlockedUntil() {
        return blockedUntil;
    }

    public String getReason() {
        return reason;
    }
}