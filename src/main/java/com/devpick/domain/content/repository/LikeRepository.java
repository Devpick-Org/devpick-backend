package com.devpick.domain.content.repository;

import com.devpick.domain.content.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LikeRepository extends JpaRepository<Like, UUID> {

    boolean existsByUser_IdAndContent_Id(UUID userId, UUID contentId);

    Optional<Like> findByUser_IdAndContent_Id(UUID userId, UUID contentId);

    // DP-249: 유저의 좋아요 목록 (최신순)
    @Query("SELECT l FROM Like l JOIN FETCH l.content WHERE l.user.id = :userId ORDER BY l.createdAt DESC")
    List<Like> findByUserIdWithContent(@Param("userId") UUID userId);
}
