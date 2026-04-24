package com.devpick.domain.content.repository;

import com.devpick.domain.content.entity.Scrap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ScrapRepository extends JpaRepository<Scrap, UUID> {

    boolean existsByUser_IdAndContent_Id(UUID userId, UUID contentId);

    Optional<Scrap> findByUser_IdAndContent_Id(UUID userId, UUID contentId);

    @Query(value = "SELECT s FROM Scrap s JOIN FETCH s.content c JOIN FETCH c.source src " +
                   "WHERE s.user.id = :userId AND c.isAvailable = true " +
                   "AND (:q IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "OR LOWER(src.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
                   "OR LOWER(c.preview) LIKE LOWER(CONCAT('%', :q, '%')))",
           countQuery = "SELECT COUNT(s) FROM Scrap s JOIN s.content c JOIN c.source src " +
                        "WHERE s.user.id = :userId AND c.isAvailable = true " +
                        "AND (:q IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
                        "OR LOWER(src.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
                        "OR LOWER(c.preview) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Scrap> findScrapsWithSearch(@Param("userId") UUID userId, @Param("q") String q, Pageable pageable);
}
