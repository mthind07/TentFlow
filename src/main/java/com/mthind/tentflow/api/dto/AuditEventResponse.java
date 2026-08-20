package com.mthind.tentflow.api.dto;

import com.mthind.tentflow.audit.AuditOutcome;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        long id,
        Instant occurredAt,
        UUID requestId,
        Long actorUserId,
        String actorEmail,
        String action,
        String resourceType,
        String resourceId,
        String oldStatus,
        String newStatus,
        AuditOutcome outcome,
        String details
) {
}
