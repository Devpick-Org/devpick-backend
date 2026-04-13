package com.devpick.domain.point.repository;

import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.entity.PointLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PointLogRepository extends JpaRepository<PointLog, UUID> {

    Page<PointLog> findByUser_IdOrderByEarnedAtDesc(UUID userId, Pageable pageable);

    boolean existsByUser_IdAndActionAndReferenceId(UUID userId, PointAction action, UUID referenceId);

    boolean existsByUser_IdAndActionAndEarnedAtBetween(
            UUID userId, PointAction action, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COALESCE(SUM(pl.points), 0) FROM PointLog pl " +
            "WHERE pl.user.id = :userId AND pl.earnedAt >= :from AND pl.earnedAt <= :to")
    int sumPointsByUserIdAndEarnedAtBetween(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    boolean existsByUser_IdAndAction(UUID userId, PointAction action);

    long countByUser_IdAndAction(UUID userId, PointAction action);

    @Query("SELECT pl FROM PointLog pl " +
            "WHERE pl.user.id = :userId AND pl.action = 'DAILY_LOGIN' " +
            "ORDER BY pl.earnedAt DESC")
    List<PointLog> findDailyLoginsByUserIdOrderByEarnedAtDesc(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM PointLog pl WHERE pl.user.id = :userId AND pl.action = :action AND pl.referenceId = :referenceId")
    void deleteByUser_IdAndActionAndReferenceId(
            @Param("userId") UUID userId,
            @Param("action") PointAction action,
            @Param("referenceId") UUID referenceId);

    @Query("SELECT COALESCE(SUM(pl.points), 0) FROM PointLog pl WHERE pl.user.id = :userId AND pl.action = :action AND pl.referenceId = :referenceId")
    int sumPointsByUser_IdAndActionAndReferenceId(
            @Param("userId") UUID userId,
            @Param("action") PointAction action,
            @Param("referenceId") UUID referenceId);

    Optional<PointLog> findTopByUser_IdAndActionOrderByEarnedAtDesc(UUID userId, PointAction action);
}