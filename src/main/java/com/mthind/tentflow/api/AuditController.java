package com.mthind.tentflow.api;

import com.mthind.tentflow.api.dto.AuditEventResponse;
import com.mthind.tentflow.api.dto.AuditPageResponse;
import com.mthind.tentflow.persistence.entity.AuditEventEntity;
import com.mthind.tentflow.persistence.repository.AuditEventRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@Validated
@RestController
@RequestMapping("/api/admin/audit-events")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditEventRepository auditEventRepository;

    public AuditController(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @GetMapping
    public AuditPageResponse getAuditEvents(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        Page<AuditEventEntity> result = auditEventRepository
                .findAllByOrderByOccurredAtDescIdDesc(
                        PageRequest.of(page, size)
                );

        return new AuditPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    private AuditEventResponse toResponse(AuditEventEntity entity) {
        return new AuditEventResponse(
                Objects.requireNonNull(entity.getId()),
                entity.getOccurredAt(),
                entity.getRequestId(),
                entity.getActor() == null ? null : entity.getActor().getId(),
                entity.getActorEmail(),
                entity.getAction(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getOldStatus(),
                entity.getNewStatus(),
                entity.getOutcome(),
                entity.getDetails()
        );
    }
}