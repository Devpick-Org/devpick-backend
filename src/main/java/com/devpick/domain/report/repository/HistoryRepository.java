package com.devpick.domain.report.repository;

import com.devpick.domain.report.entity.History;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface HistoryRepository extends JpaRepository<History, UUID> {

    long countByUser_IdAndActionTypeAndCreatedAtBetween(
            UUID userId, String actionType, LocalDateTime from, LocalDateTime to);

    @Query("SELECT ct.tag.name, COUNT(ct.tag.name) FROM History h " +
           "JOIN h.content c JOIN c.contentTags ct " +
           "WHERE h.user.id = :userId AND h.actionType = 'content_opened' " +
           "AND h.createdAt BETWEEN :from AND :to " +
           "GROUP BY ct.tag.name ORDER BY COUNT(ct.tag.name) DESC")
    List<Object[]> findTopTagsByUserAndPeriod(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // 섹션 2 바 차트: 요일별 활동 수 (ISODOW: 1=월 ~ 7=일)
    @Query(value = "SELECT EXTRACT(ISODOW FROM h.created_at) AS dow, COUNT(*) AS cnt " +
                   "FROM history h " +
                   "WHERE h.user_id = :userId AND h.created_at BETWEEN :from AND :to " +
                   "GROUP BY EXTRACT(ISODOW FROM h.created_at) " +
                   "ORDER BY dow",
           nativeQuery = true)
    List<Object[]> findDailyActivityCountsByUserAndPeriod(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // DP-293: 2단계 페이징 - 1단계: ID만 조회 (actionType 필터 있을 때)
    @Query(value = "SELECT h.id FROM History h " +
                   "WHERE h.user.id = :userId " +
                   "AND h.actionType IN :actionTypes " +
                   "AND (:startDate IS NULL OR h.createdAt >= :startDate) " +
                   "AND (:endDate IS NULL OR h.createdAt <= :endDate) " +
                   "ORDER BY h.createdAt DESC",
           countQuery = "SELECT COUNT(h) FROM History h " +
                        "WHERE h.user.id = :userId " +
                        "AND h.actionType IN :actionTypes " +
                        "AND (:startDate IS NULL OR h.createdAt >= :startDate) " +
                        "AND (:endDate IS NULL OR h.createdAt <= :endDate)")
    Page<UUID> findHistoryIdsByActionTypesAndDateRange(
            @Param("userId") UUID userId,
            @Param("actionTypes") List<String> actionTypes,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // DP-293: 2단계 페이징 - 1단계: ID만 조회 (actionType 필터 없을 때)
    @Query(value = "SELECT h.id FROM History h " +
                   "WHERE h.user.id = :userId " +
                   "AND (:startDate IS NULL OR h.createdAt >= :startDate) " +
                   "AND (:endDate IS NULL OR h.createdAt <= :endDate) " +
                   "ORDER BY h.createdAt DESC",
           countQuery = "SELECT COUNT(h) FROM History h " +
                        "WHERE h.user.id = :userId " +
                        "AND (:startDate IS NULL OR h.createdAt >= :startDate) " +
                        "AND (:endDate IS NULL OR h.createdAt <= :endDate)")
    Page<UUID> findHistoryIdsByDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /** 학습 히스토리: content_liked 제외 (GET /history 기본) */
    @Query(value = "SELECT h.id FROM History h " +
                   "WHERE h.user.id = :userId " +
                   "AND h.actionType <> 'content_liked' " +
                   "AND (:startDate IS NULL OR h.createdAt >= :startDate) " +
                   "AND (:endDate IS NULL OR h.createdAt <= :endDate) " +
                   "ORDER BY h.createdAt DESC",
           countQuery = "SELECT COUNT(h) FROM History h " +
                        "WHERE h.user.id = :userId " +
                        "AND h.actionType <> 'content_liked' " +
                        "AND (:startDate IS NULL OR h.createdAt >= :startDate) " +
                        "AND (:endDate IS NULL OR h.createdAt <= :endDate)")
    Page<UUID> findHistoryIdsByDateRangeExcludingContentLiked(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // DP-293: 2단계 페이징 - 2단계: ID 목록으로 연관 엔티티 FETCH JOIN (최대 pageSize개)
    @Query("SELECT h FROM History h " +
           "LEFT JOIN FETCH h.content " +
           "LEFT JOIN FETCH h.post " +
           "LEFT JOIN FETCH h.answer " +
           "WHERE h.id IN :ids " +
           "ORDER BY h.createdAt DESC")
    List<History> findHistoriesWithAssociationsByIds(@Param("ids") List<UUID> ids);
}
