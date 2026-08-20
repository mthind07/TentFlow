package com.mthind.tentflow.audit;

import com.mthind.tentflow.persistence.entity.AuditEventEntity;
import com.mthind.tentflow.persistence.entity.UserAccountEntity;
import com.mthind.tentflow.persistence.repository.AuditEventRepository;
import com.mthind.tentflow.persistence.repository.UserAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class AuditWriter {

    private final AuditEventRepository auditEventRepository;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public AuditWriter(
            AuditEventRepository auditEventRepository,
            UserAccountRepository userAccountRepository,
            Clock clock
    ) {
        this.auditEventRepository = auditEventRepository;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void appendRequired(AuditRecord record) {
        append(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendRequiresNew(AuditRecord record) {
        append(record);
    }

    private void append(AuditRecord record) {
        UserAccountEntity actor = record.actorUserId() == null
                ? null
                : userAccountRepository.findById(record.actorUserId())
                .orElse(null);

        auditEventRepository.save(new AuditEventEntity(
                Instant.now(clock).truncatedTo(ChronoUnit.MICROS),
                record.requestId(),
                actor,
                record.actorEmail(),
                record.action(),
                record.resourceType(),
                record.resourceId(),
                record.oldStatus(),
                record.newStatus(),
                record.outcome(),
                record.details()
        ));
    }
}