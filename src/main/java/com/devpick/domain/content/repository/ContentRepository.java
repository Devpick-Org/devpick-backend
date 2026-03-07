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

    Page<Content> findByIsAvailableTrueOrderByPublishedAtDesc(Pageable pageable);

    Optional<Content> findByIdAndIsAvailableTrue(UUID id);

    @Query("SELECT DISTINCT c FROM Content c JOIN c.contentTags ct WHERE ct.tag.id IN :tagIds AND c.isAvailable = true ORDER BY c.publishedAt DESC")
    Page<Content> findByTagIdsAndIsAvailableTrue(@Param("tagIds") List<UUID> tagIds, Pageable pageable);

    @Query("SELECT DISTINCT c FROM Content c LEFT JOIN c.contentTags ct LEFT JOIN ct.tag t " +
           "WHERE c.isAvailable = true " +
           "AND (:query IS NULL OR c.title LIKE %:query% OR c.author LIKE %:query%) " +
           "AND (:#{#tags == null || #tags.isEmpty()} = true OR t.name IN :tags) " +
           "ORDER BY c.publishedAt DESC")
    Page<Content> searchContents(@Param("query") String query, @Param("tags") List<String> tags, Pageable pageable);
}
