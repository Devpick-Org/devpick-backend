package com.devpick.domain.report.repository;

import com.devpick.domain.report.entity.WeeklyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, UUID> {

    Optional<WeeklyReport> findByUser_IdAndWeekStart(UUID userId, LocalDate weekStart);

    boolean existsByUser_IdAndWeekStart(UUID userId, LocalDate weekStart);

    Optional<WeeklyReport> findByShareToken(String shareToken);

    @Query("SELECT r FROM WeeklyReport r WHERE r.user.id = :userId ORDER BY r.weekStart DESC")
    List<WeeklyReport> findByUserIdOrderByWeekStartDesc(@Param("userId") UUID userId);

    @Query("SELECT r FROM WeeklyReport r JOIN FETCH r.activities WHERE r.user.id = :userId AND r.weekStart = :weekStart")
    Optional<WeeklyReport> findWithActivitiesByUser_IdAndWeekStart(
            @Param("userId") UUID userId, @Param("weekStart") LocalDate weekStart);

    @Query("SELECT r FROM WeeklyReport r JOIN FETCH r.activities WHERE r.id = :id")
    Optional<WeeklyReport> findWithActivitiesById(@Param("id") UUID id);

    @Query("SELECT r FROM WeeklyReport r JOIN FETCH r.activities WHERE r.shareToken = :token")
    Optional<WeeklyReport> findWithActivitiesByShareToken(@Param("token") String token);
}
