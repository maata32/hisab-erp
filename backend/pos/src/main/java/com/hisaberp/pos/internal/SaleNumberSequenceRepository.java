package com.hisaberp.pos.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface SaleNumberSequenceRepository extends JpaRepository<SaleNumberSequence, java.util.UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SaleNumberSequence s WHERE s.year = :year")
    Optional<SaleNumberSequence> lockByYear(@Param("year") int year);
}
