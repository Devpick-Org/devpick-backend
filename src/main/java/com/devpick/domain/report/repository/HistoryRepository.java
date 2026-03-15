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

    // DP-248: 학습 히스토리 조회 (content_liked 제외, 페이지네이션)
    @Query(value = "SELECT h FROM History h " +
                   "LEFT JOIN FETCH h.content " +
                   "LEFT JOIN FETCH h.post " +
                   "WHERE h.user.id = :userId AND h.actionType <> 'content_liked' " +
                   "ORDER BY h.createdAt DESC",
           countQuery = "SELECT COUNT(h) FROM History h " +
                        "WHERE h.user.id = :userId AND h.actionType <> 'content_liked'")
    Page<History> findLearningHistoryByUserId(@Param("userId") UUID userId, Pageable pageable);

    // DP-249: 전체 활동 내역 조회 (content_liked 포함, 페이지네이션)
    @Query(value = "SELECT h FROM History h " +
                   "LEFT JOIN FETCH h.content " +
                   "LEFT JOIN FETCH h.post " +
                   "WHERE h.user.id = :userId " +
                   "ORDER BY h.createdAt DESC",
           countQuery = "SELECT COUNT(h) FROM History h WHERE h.user.id = :userId")
    Page<History> findAllActivityByUserId(@Param("userId") UUID userId, Pageable pageable);
}
