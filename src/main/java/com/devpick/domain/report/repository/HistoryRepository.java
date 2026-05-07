package com.devpick.domain.report.repository;

import com.devpick.domain.report.entity.History;
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

public interface HistoryRepository extends JpaRepository<History, UUID> {

    /** 도서 추천용: 태그명 + 빈도 수 조회 */
    @Query("SELECT ct.tag.name, COUNT(ct.tag.name) FROM History h " +
           "JOIN h.content c JOIN c.contentTags ct " +
           "WHERE h.user.id = :userId " +
           "AND h.actionType IN :actionTypes " +
           "AND h.createdAt >= :since " +
           "AND h.content IS NOT NULL " +
           "GROUP BY ct.tag.name ORDER BY COUNT(ct.tag.name) DESC")
    List<Object[]> findTagNameCountsByUserActionsAfter(
            @Param("userId") UUID userId,
            @Param("actionTypes") List<String> actionTypes,
            @Param("since") LocalDateTime since);

    @Query("SELECT DISTINCT ct.tag.id FROM History h " +
           "JOIN h.content c JOIN c.contentTags ct " +
           "WHERE h.user.id = :userId " +
           "AND h.actionType IN :actionTypes " +
           "AND h.createdAt >= :since " +
           "AND h.content IS NOT NULL")
    List<UUID> findDistinctTagIdsByUserActionsAfter(
            @Param("userId") UUID userId,
            @Param("actionTypes") List<String> actionTypes,
            @Param("since") LocalDateTime since);

    /** 동일 콘텐츠 AI 요약 조회 히스토리 중복 방지용 */
    boolean existsByUser_IdAndContent_IdAndActionType(
            UUID userId, UUID contentId, String actionType);

    /** 동일 공고 당일 조회 중복 방지용 */
    boolean existsByUser_IdAndJobPosting_IdAndActionTypeAndCreatedAtBetween(
            UUID userId, UUID jobPostingId, String actionType,
            LocalDateTime from, LocalDateTime to);

    @Query("SELECT MIN(h.createdAt) FROM History h WHERE h.user.id = :userId")
    Optional<LocalDateTime> findMinCreatedAtByUserId(@Param("userId") UUID userId);

    long countByUser_IdAndActionTypeAndCreatedAtBetween(
            UUID userId, String actionType, LocalDateTime from, LocalDateTime to);

    @Query("SELECT ct.tag.name, COUNT(ct.tag.name) FROM History h " +
           "JOIN h.content c JOIN c.contentTags ct " +
           "WHERE h.user.id = :userId " +
           "AND h.actionType IN ('content_opened', 'ai_summary_viewed', 'scrapped') " +
           "AND h.createdAt BETWEEN :from AND :to " +
           "GROUP BY ct.tag.name ORDER BY COUNT(ct.tag.name) DESC")
    List<Object[]> findTopTagsByUserAndPeriod(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // 섹션 2 바 차트: 요일별 활동 수 (ISODOW: 1=월 ~ 7=일), 학습 관련 action_type 화이트리스트
    @Query(value = "SELECT EXTRACT(ISODOW FROM h.created_at) AS dow, COUNT(*) AS cnt " +
                   "FROM history h " +
                   "WHERE h.user_id = :userId AND h.created_at BETWEEN :from AND :to " +
                   "AND h.action_type IN ('content_opened', 'ai_summary_viewed', 'scrapped', " +
                   "'question_created', 'post_created', 'ai_quiz_completed', 'job_posting_viewed') " +
                   "GROUP BY EXTRACT(ISODOW FROM h.created_at) " +
                   "ORDER BY dow",
           nativeQuery = true)
    List<Object[]> findDailyActivityCountsByUserAndPeriod(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // 확인 공고 분석: 조회한 공고 ID 목록
    @Query("SELECT DISTINCT h.jobPosting.id FROM History h " +
           "WHERE h.user.id = :userId AND h.actionType = 'job_posting_viewed' " +
           "AND h.createdAt BETWEEN :from AND :to AND h.jobPosting IS NOT NULL")
    List<UUID> findViewedJobPostingIdsByUserAndPeriod(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // 확인 공고 분석: 조회한 공고의 기술 스택 빈도 집계
    @Query(value = "SELECT jt.tech, COUNT(DISTINCT h.job_posting_id) AS cnt " +
                   "FROM history h " +
                   "JOIN job_posting_tech_stack jt ON jt.job_posting_id = h.job_posting_id " +
                   "WHERE h.user_id = :userId AND h.action_type = 'job_posting_viewed' " +
                   "AND h.created_at BETWEEN :from AND :to " +
                   "GROUP BY jt.tech ORDER BY cnt DESC LIMIT 10",
           nativeQuery = true)
    List<Object[]> findJobTechStackFrequencyByUserAndPeriod(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // 읽은 글 분석: 조회/스크랩한 콘텐츠 ID 목록
    @Query("SELECT DISTINCT h.content.id FROM History h " +
           "WHERE h.user.id = :userId AND h.actionType IN ('content_opened', 'scrapped') " +
           "AND h.createdAt BETWEEN :from AND :to AND h.content IS NOT NULL")
    List<UUID> findReadContentIdsByUserAndPeriod(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // 질문 분석: 이번 주 작성한 게시글 ID 목록
    @Query("SELECT DISTINCT h.post.id FROM History h " +
           "WHERE h.user.id = :userId AND h.actionType = 'question_created' " +
           "AND h.createdAt BETWEEN :from AND :to AND h.post IS NOT NULL")
    List<UUID> findCreatedPostIdsByUserAndPeriod(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // DP-293: 2단계 페이징 - 1단계: ID만 조회 (actionType 필터 있을 때, 날짜 필터 없음)
    @Query(value = "SELECT h.id FROM History h " +
                   "WHERE h.user.id = :userId " +
                   "AND h.actionType IN :actionTypes " +
                   "ORDER BY h.createdAt DESC",
           countQuery = "SELECT COUNT(h) FROM History h " +
                        "WHERE h.user.id = :userId " +
                        "AND h.actionType IN :actionTypes")
    Page<UUID> findHistoryIdsByActionTypes(
            @Param("userId") UUID userId,
            @Param("actionTypes") List<String> actionTypes,
            Pageable pageable);

    // DP-293: 2단계 페이징 - 1단계: ID만 조회 (actionType 필터 있을 때, 날짜 필터 있음)
    @Query(value = "SELECT h.id FROM History h " +
                   "WHERE h.user.id = :userId " +
                   "AND h.actionType IN :actionTypes " +
                   "AND h.createdAt >= :startDate " +
                   "AND h.createdAt <= :endDate " +
                   "ORDER BY h.createdAt DESC",
           countQuery = "SELECT COUNT(h) FROM History h " +
                        "WHERE h.user.id = :userId " +
                        "AND h.actionType IN :actionTypes " +
                        "AND h.createdAt >= :startDate " +
                        "AND h.createdAt <= :endDate")
    Page<UUID> findHistoryIdsByActionTypesAndDateRange(
            @Param("userId") UUID userId,
            @Param("actionTypes") List<String> actionTypes,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // DP-293: 2단계 페이징 - 1단계: ID만 조회 (전체, 날짜 필터 없음)
    @Query(value = "SELECT h.id FROM History h " +
                   "WHERE h.user.id = :userId " +
                   "ORDER BY h.createdAt DESC",
           countQuery = "SELECT COUNT(h) FROM History h " +
                        "WHERE h.user.id = :userId")
    Page<UUID> findAllHistoryIds(
            @Param("userId") UUID userId,
            Pageable pageable);

    /** 학습 히스토리: content_liked 제외, 날짜 필터 없음 */
    @Query(value = "SELECT h.id FROM History h " +
                   "WHERE h.user.id = :userId " +
                   "AND h.actionType <> 'content_liked' " +
                   "ORDER BY h.createdAt DESC",
           countQuery = "SELECT COUNT(h) FROM History h " +
                        "WHERE h.user.id = :userId " +
                        "AND h.actionType <> 'content_liked'")
    Page<UUID> findHistoryIdsExcludingContentLiked(
            @Param("userId") UUID userId,
            Pageable pageable);

    /** 학습 히스토리: content_liked 제외, 날짜 필터 있음 */
    @Query(value = "SELECT h.id FROM History h " +
                   "WHERE h.user.id = :userId " +
                   "AND h.actionType <> 'content_liked' " +
                   "AND h.createdAt >= :startDate " +
                   "AND h.createdAt <= :endDate " +
                   "ORDER BY h.createdAt DESC",
           countQuery = "SELECT COUNT(h) FROM History h " +
                        "WHERE h.user.id = :userId " +
                        "AND h.actionType <> 'content_liked' " +
                        "AND h.createdAt >= :startDate " +
                        "AND h.createdAt <= :endDate")
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
           "LEFT JOIN FETCH h.comment " +
           "LEFT JOIN FETCH h.jobPosting " +
           "WHERE h.id IN :ids " +
           "ORDER BY h.createdAt DESC")
    List<History> findHistoriesWithAssociationsByIds(@Param("ids") List<UUID> ids);

    @Modifying
    @Query("DELETE FROM History h WHERE h.post.id = :postId")
    void deleteByPostId(@Param("postId") UUID postId);

    @Modifying
    @Query("DELETE FROM History h WHERE h.answer.id = :answerId")
    void deleteByAnswerId(@Param("answerId") UUID answerId);

    @Modifying
    @Query("DELETE FROM History h WHERE h.answer.id IN (SELECT a.id FROM Answer a WHERE a.post.id = :postId)")
    void deleteByAnswerPostId(@Param("postId") UUID postId);

    @Modifying
    @Query("DELETE FROM History h WHERE h.comment.id = :commentId")
    void deleteByCommentId(@Param("commentId") UUID commentId);

    @Modifying
    @Query("DELETE FROM History h WHERE h.user.id = :userId AND h.content.id = :contentId AND h.actionType = :actionType")
    void deleteByUserIdAndContentIdAndActionType(
            @Param("userId") UUID userId,
            @Param("contentId") UUID contentId,
            @Param("actionType") String actionType);
}
