package com.mthind.tentflow.persistence.repository;

import com.mthind.tentflow.persistence.entity.MaintenanceBlockEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MaintenanceBlockRepository
        extends JpaRepository<MaintenanceBlockEntity, Long> {

    @EntityGraph(attributePaths = "tentType")
    @Query("""
            SELECT block
            FROM MaintenanceBlockEntity block
            WHERE block.id = :maintenanceBlockId
            """)
    Optional<MaintenanceBlockEntity> findDetailedById(
            @Param("maintenanceBlockId") long maintenanceBlockId
    );

    @EntityGraph(attributePaths = "tentType")
    List<MaintenanceBlockEntity> findAllByOrderByIdAsc();

    @Query("""
            SELECT block.tentType.id
            FROM MaintenanceBlockEntity block
            WHERE block.id = :maintenanceBlockId
            """)
    Optional<Long> findTentTypeIdByMaintenanceBlockId(
            @Param("maintenanceBlockId") long maintenanceBlockId
    );

    @Query("""
            SELECT block
            FROM MaintenanceBlockEntity block
            WHERE block.tentType.id = :tentTypeId
              AND block.blockedFrom < :until
              AND block.blockedUntil > :from
            ORDER BY block.id
            """)
    List<MaintenanceBlockEntity> findOverlapping(
            @Param("tentTypeId") long tentTypeId,
            @Param("from") LocalDateTime from,
            @Param("until") LocalDateTime until
    );
}
