package com.devpick.domain.content.repository;

import com.devpick.domain.content.entity.Content;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentRepository extends JpaRepository<Content, UUID> {

    @Query("SELECT c FROM Content c WHERE c.isAvailable = true AND c.source.name <> 'YouTube' ORDER BY c.publishedAt DESC")
    Page<Content> findByIsAvailableTrueOrderByPublishedAtDesc(Pageable pageable);

    Optional<Content> findByIdAndIsAvailableTrue(UUID id);

    @Query("SELECT DISTINCT c FROM Content c JOIN c.contentTags ct WHERE ct.tag.id IN :tagIds AND c.isAvailable = true AND c.source.name <> 'YouTube' ORDER BY c.publishedAt DESC")
    Page<Content> findByTagIdsAndIsAvailableTrue(@Param("tagIds") List<UUID> tagIds, Pageable pageable);

    @Query("""
            SELECT c FROM Content c
            WHERE c.isAvailable = true AND c.source.name <> 'YouTube'
            ORDER BY
              CASE WHEN EXISTS (
                SELECT ct FROM ContentTag ct WHERE ct.content = c AND ct.tag.id IN :tagIds
              ) THEN 0 ELSE 1 END,
              c.publishedAt DESC
            """)
    Page<Content> findAllRankedByTagIds(@Param("tagIds") List<UUID> tagIds, Pageable pageable);

    @Query("SELECT DISTINCT c FROM Content c LEFT JOIN c.contentTags ct LEFT JOIN ct.tag t " +
           "WHERE c.isAvailable = true " +
           "AND c.source.name <> 'YouTube' " +
           "AND (COALESCE(TRIM(:query), '') = '' OR " +
           "LOWER(c.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.translatedTitle) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.author) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:#{#tags == null || #tags.isEmpty()} = true OR LOWER(t.name) IN :tags) " +
           "ORDER BY c.publishedAt DESC")
    Page<Content> searchContents(@Param("query") String query, @Param("tags") List<String> tags, Pageable pageable);

    @Query("SELECT DISTINCT c FROM Content c JOIN c.contentTags ct WHERE ct.tag.id IN :tagIds AND c.isAvailable = true AND c.source.name <> 'YouTube' AND c.id <> :excludeId ORDER BY c.publishedAt DESC")
    Page<Content> findRecommendationsByTagIds(@Param("tagIds") List<UUID> tagIds, @Param("excludeId") UUID excludeId, Pageable pageable);

    @Query("SELECT c FROM Content c " +
           "WHERE c.isAvailable = true " +
           "AND c.source.name <> 'YouTube' " +
           "AND (LOWER(c.title) LIKE LOWER(CONCAT('%', :tagName, '%')) " +
           "OR LOWER(c.preview) LIKE LOWER(CONCAT('%', :tagName, '%'))) " +
           "AND NOT EXISTS (SELECT s FROM Scrap s WHERE s.user.id = :userId AND s.content = c) " +
           "ORDER BY c.publishedAt DESC")
    List<Content> findByTagNameInTitleExcludingYoutubeAndScrapped(
            @Param("tagName") String tagName,
            @Param("userId") UUID userId,
            Pageable pageable);

    @Query("SELECT c FROM Content c " +
           "WHERE c.isAvailable = true " +
           "AND c.source.name <> 'YouTube' " +
           "AND NOT EXISTS (SELECT s FROM Scrap s WHERE s.user.id = :userId AND s.content = c) " +
           "ORDER BY c.publishedAt DESC")
    List<Content> findLatestExcludingYoutubeAndScrapped(
            @Param("userId") UUID userId,
            Pageable pageable);

    @Query("SELECT c FROM Content c " +
           "WHERE c.isAvailable = true " +
           "AND c.source.name = 'YouTube' " +
           "AND NOT EXISTS (SELECT s FROM Scrap s WHERE s.user.id = :userId AND s.content = c) " +
           "ORDER BY c.publishedAt DESC")
    List<Content> findLatestYoutubeExcludingScrapped(
            @Param("userId") UUID userId,
            Pageable pageable);

    /** YouTube 추천용: content_tags JOIN, 스크랩 제외 */
    @Query("SELECT DISTINCT c FROM Content c JOIN c.contentTags ct " +
           "WHERE c.isAvailable = true " +
           "AND c.source.name = 'YouTube' " +
           "AND ct.tag.id IN :tagIds " +
           "AND NOT EXISTS (SELECT s FROM Scrap s WHERE s.user.id = :userId AND s.content = c) " +
           "ORDER BY c.publishedAt DESC")
    List<Content> findYoutubeByTagIdsExcludingScrapped(
            @Param("tagIds") List<UUID> tagIds,
            @Param("userId") UUID userId,
            Pageable pageable);

    /** YouTube 추천용: 탐색 여지 — 관심 태그 외 영역의 YouTube 영상 */
    @Query("SELECT DISTINCT c FROM Content c JOIN c.contentTags ct " +
           "WHERE c.isAvailable = true " +
           "AND c.source.name = 'YouTube' " +
           "AND ct.tag.id NOT IN :excludeTagIds " +
           "AND NOT EXISTS (SELECT s FROM Scrap s WHERE s.user.id = :userId AND s.content = c) " +
           "ORDER BY c.publishedAt DESC")
    List<Content> findYoutubeByExcludeTagIdsExcludingScrapped(
            @Param("excludeTagIds") List<UUID> excludeTagIds,
            @Param("userId") UUID userId,
            Pageable pageable);
}
