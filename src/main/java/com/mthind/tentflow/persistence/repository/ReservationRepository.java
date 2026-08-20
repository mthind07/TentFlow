package com.mthind.tentflow.persistence.repository;

import com.mthind.tentflow.model.ReservationStatus;
import com.mthind.tentflow.persistence.entity.ReservationEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository
        extends JpaRepository<ReservationEntity, Long> {

    @EntityGraph(attributePaths = {"customer", "tentType"})
    @Query("""
            SELECT reservation
            FROM ReservationEntity reservation
            WHERE reservation.id = :reservationId
            """)
    Optional<ReservationEntity> findDetailedById(
            @Param("reservationId") long reservationId
    );

    @EntityGraph(attributePaths = {"customer", "tentType"})
    List<ReservationEntity> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = {"customer", "tentType"})
    List<ReservationEntity> findAllByCustomerIdOrderByIdAsc(long customerId);

    @Query("""
            SELECT reservation.customer.id
            FROM ReservationEntity reservation
            WHERE reservation.id = :reservationId
            """)
    Optional<Long> findCustomerIdByReservationId(
            @Param("reservationId") long reservationId
    );

    @Query("""
            SELECT reservation.tentType.id
            FROM ReservationEntity reservation
            WHERE reservation.id = :reservationId
            """)
    Optional<Long> findTentTypeIdByReservationId(
            @Param("reservationId") long reservationId
    );

    @Query("""
            SELECT reservation.id
            FROM ReservationEntity reservation
            WHERE reservation.status = :status
              AND reservation.reservedUntil <= :now
            ORDER BY reservation.id
            """)
    List<Long> findDueConfirmedIds(
            @Param("status") ReservationStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            SELECT reservation.status
            FROM ReservationEntity reservation
            WHERE reservation.id = :reservationId
            """)
    Optional<ReservationStatus> findStatusByReservationId(
            @Param("reservationId") long reservationId
    );

    @Query("""
            SELECT reservation.reservedUntil
            FROM ReservationEntity reservation
            WHERE reservation.id = :reservationId
            """)
    Optional<LocalDateTime> findReservedUntilByReservationId(
            @Param("reservationId") long reservationId
    );

    @Query("""
            SELECT reservation
            FROM ReservationEntity reservation
            WHERE reservation.tentType.id = :tentTypeId
              AND reservation.status IN :statuses
              AND reservation.reservedFrom < :until
              AND reservation.reservedUntil > :from
            ORDER BY reservation.id
            """)
    List<ReservationEntity> findOverlappingByStatus(
            @Param("tentTypeId") long tentTypeId,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("from") LocalDateTime from,
            @Param("until") LocalDateTime until
    );
}