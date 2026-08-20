package com.mthind.tentflow.persistence.entity;

import com.mthind.tentflow.audit.AuditOutcome;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private UserAccountEntity actor;

    @Column(name = "actor_email", nullable = false, length = 254)
    private String actorEmail;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 80)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "old_status", length = 20)
    private String oldStatus;

    @Column(name = "new_status", length = 20)
    private String newStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditOutcome outcome;

    @Column(length = 500)
    private String details;

    protected AuditEventEntity() {
        // Required by JPA.
    }

    public AuditEventEntity(
            Instant occurredAt,
            UUID requestId,
            UserAccountEntity actor,
            String actorEmail,
            String action,
            String resourceType,
            String resourceId,
            String oldStatus,
            String newStatus,
            AuditOutcome outcome,
            String details
    ) {
        this.occurredAt = occurredAt;
        this.requestId = requestId;
        this.actor = actor;
        this.actorEmail = actorEmail;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.outcome = outcome;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UserAccountEntity getActor() {
        return actor;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getOldStatus() {
        return oldStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public String getDetails() {
        return details;
    }
}