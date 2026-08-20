package com.mthind.tentflow.audit;

import java.util.UUID;

public record AuditRecord(
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