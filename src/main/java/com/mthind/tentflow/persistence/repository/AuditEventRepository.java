package com.mthind.tentflow.persistence.repository;

import com.mthind.tentflow.persistence.entity.AuditEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository
        extends JpaRepository<AuditEventEntity, Long> {

    @EntityGraph(attributePaths = "actor")
    Page<AuditEventEntity> findAllByOrderByOccurredAtDescIdDesc(
            Pageable pageable
    );
}