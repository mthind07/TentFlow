package com.mthind.tentflow.audit;

import com.mthind.tentflow.security.RequestIdFilter;
import com.mthind.tentflow.security.SecurityIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Service
public class AuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            AuditService.class
    );
    private static final String ANONYMOUS = "anonymous";

    private final AuditWriter auditWriter;
    private final SecurityIdentity securityIdentity;

    public AuditService(
            AuditWriter auditWriter,
            SecurityIdentity securityIdentity
    ) {
        this.auditWriter = auditWriter;
        this.securityIdentity = securityIdentity;
    }

    public void success(
            String action,
            String resourceType,
            Object resourceId,
            String details
    ) {
        SecurityIdentity.CurrentUser user = securityIdentity.currentUser()
                .orElse(new SecurityIdentity.CurrentUser(
                        null,
                        ANONYMOUS,
                        null,
                        null
                ));

        auditWriter.appendRequired(record(
                user.userId(),
                user.email(),
                action,
                resourceType,
                resourceId,
                null,
                null,
                AuditOutcome.SUCCEEDED,
                details
        ));
    }

    public void successAs(
            Long actorUserId,
            String actorEmail,
            String action,
            String resourceType,
            Object resourceId,
            String details
    ) {
        auditWriter.appendRequired(record(
                actorUserId,
                actorEmail,
                action,
                resourceType,
                resourceId,
                null,
                null,
                AuditOutcome.SUCCEEDED,
                details
        ));
    }

    public void failureInNewTransaction(
            String action,
            String resourceType,
            Object resourceId,
            String details
    ) {
        SecurityIdentity.CurrentUser user = securityIdentity.currentUser()
                .orElse(new SecurityIdentity.CurrentUser(
                        null,
                        ANONYMOUS,
                        null,
                        null
                ));

        try {
            auditWriter.appendRequiresNew(record(
                    user.userId(),
                    user.email(),
                    action,
                    resourceType,
                    resourceId,
                    null,
                    null,
                    AuditOutcome.FAILED,
                    details
            ));
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Could not persist failed-operation audit event {}",
                    action,
                    exception
            );
        }
    }

    public void loginFailure() {
        try {
            auditWriter.appendRequiresNew(record(
                    null,
                    ANONYMOUS,
                    "LOGIN",
                    "AUTHENTICATION",
                    null,
                    null,
                    null,
                    AuditOutcome.FAILED,
                    null
            ));
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Could not persist failed-login audit event",
                    exception
            );
        }
    }

    public void lifecycleSuccess(
            String action,
            Object resourceId,
            Object oldStatus,
            Object newStatus,
            String details
    ) {
        SecurityIdentity.CurrentUser user = securityIdentity.currentUser()
                .orElse(new SecurityIdentity.CurrentUser(
                        null,
                        ANONYMOUS,
                        null,
                        null
                ));
        auditWriter.appendRequired(record(
                user.userId(),
                user.email(),
                action,
                "RESERVATION",
                resourceId,
                oldStatus,
                newStatus,
                AuditOutcome.SUCCEEDED,
                details
        ));
    }

    public void lifecycleSuccessAs(
            String actorEmail,
            String action,
            Object resourceId,
            Object oldStatus,
            Object newStatus,
            String details
    ) {
        auditWriter.appendRequired(record(
                null,
                actorEmail,
                action,
                "RESERVATION",
                resourceId,
                oldStatus,
                newStatus,
                AuditOutcome.SUCCEEDED,
                details
        ));
    }

    private AuditRecord record(
            Long actorUserId,
            String actorEmail,
            String action,
            String resourceType,
            Object resourceId,
            Object oldStatus,
            Object newStatus,
            AuditOutcome outcome,
            String details
    ) {
        return new AuditRecord(
                currentRequestId(),
                actorUserId,
                actorEmail == null || actorEmail.isBlank()
                        ? ANONYMOUS
                        : truncate(actorEmail, 254),
                truncate(action, 80),
                truncate(resourceType, 80),
                resourceId == null
                        ? null
                        : truncate(resourceId.toString(), 100),
                oldStatus == null
                        ? null
                        : truncate(oldStatus.toString(), 20),
                newStatus == null
                        ? null
                        : truncate(newStatus.toString(), 20),
                outcome,
                details == null ? null : truncate(details, 500)
        );
    }

    private UUID currentRequestId() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return RequestIdFilter.currentRequestId(request);
        }
        return UUID.randomUUID();
    }

    private String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }
}