package com.devpick.domain.trend.repository;

import com.devpick.domain.trend.entity.TrendSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TrendSnapshotRepository extends JpaRepository<TrendSnapshot, UUID> {
    Optional<TrendSnapshot> findFirstByUnitAndScopeOrderByPeriodStartDesc(String unit, String scope);
    Optional<TrendSnapshot> findByUnitAndScopeAndPeriodStart(String unit, String scope, LocalDate periodStart);
}
