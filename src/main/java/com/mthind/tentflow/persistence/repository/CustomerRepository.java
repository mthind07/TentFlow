package com.mthind.tentflow.persistence.repository;

import com.mthind.tentflow.persistence.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository
        extends JpaRepository<CustomerEntity, Long> {

    @Query("""
            SELECT (count(customer) > 0)
            FROM CustomerEntity customer
            WHERE lower(customer.email) = lower(:email)
            """)
    boolean existsByNormalizedEmail(@Param("email") String email);

    List<CustomerEntity> findAllByOrderByIdAsc();
}