package com.mthind.tentflow.persistence.repository;

import com.mthind.tentflow.persistence.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository
        extends JpaRepository<UserAccountEntity, Long> {

    @EntityGraph(attributePaths = "customer")
    @Query("""
            SELECT account
            FROM UserAccountEntity account
            WHERE lower(account.email) = lower(:email)
            """)
    Optional<UserAccountEntity> findDetailedByEmail(
            @Param("email") String email
    );

    @Query("""
            SELECT (count(account) > 0)
            FROM UserAccountEntity account
            WHERE lower(account.email) = lower(:email)
            """)
    boolean existsByNormalizedEmail(@Param("email") String email);

    @EntityGraph(attributePaths = "customer")
    List<UserAccountEntity> findAllByOrderByIdAsc();
}
