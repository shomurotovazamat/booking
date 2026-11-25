package com.example.booking.repository;

import com.example.booking.model.Resource;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRepository extends JpaRepository<Resource, Long> {

    boolean existsByName(String name);

    /**
     * SELECT ... FOR UPDATE: блокирует строку ресурса до конца транзакции,
     * поэтому параллельные запросы на один ресурс выполняются по очереди.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id")
    Optional<Resource> findByIdForUpdate(@Param("id") Long id);
}
