package com.mthind.tentflow.persistence.repository;

import com.mthind.tentflow.persistence.entity.WaitlistEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WaitlistEntryRepository
        extends JpaRepository<WaitlistEntryEntity, Long> {

    @Query("""
            SELECT entry
            FROM WaitlistEntryEntity entry
            JOIN FETCH entry.reservation reservation
            JOIN FETCH reservation.customer
            JOIN FETCH reservation.tentType
            WHERE entry.reservation.tentType.id = :tentTypeId
            ORDER BY entry.joinedAt, entry.id
            """)
    List<WaitlistEntryEntity> findOrderedByTentTypeId(
            @Param("tentTypeId") long tentTypeId
    );

    @Modifying
    @Query("""
            DELETE FROM WaitlistEntryEntity entry
            WHERE entry.reservation.id = :reservationId
            """)
    int deleteByReservationId(
            @Param("reservationId") long reservationId
    );
}