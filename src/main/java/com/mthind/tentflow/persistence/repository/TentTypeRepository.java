package com.mthind.tentflow.persistence.repository;

import com.mthind.tentflow.persistence.entity.TentTypeEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TentTypeRepository
        extends JpaRepository<TentTypeEntity, Long> {

    boolean existsByWidthFeetAndLengthFeet(
            int widthFeet,
            int lengthFeet
    );

    List<TentTypeEntity> findAllByOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT tent
            FROM TentTypeEntity tent
            WHERE tent.id = :tentTypeId
            """)
    Optional<TentTypeEntity> findByIdForUpdate(
            @Param("tentTypeId") long tentTypeId
    );
}