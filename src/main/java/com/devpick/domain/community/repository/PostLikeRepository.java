package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    boolean existsByPost_IdAndUser_Id(UUID postId, UUID userId);

    Optional<PostLike> findByPost_IdAndUser_Id(UUID postId, UUID userId);

    long countByPost_Id(UUID postId);

    @Modifying
    @Query("DELETE FROM PostLike pl WHERE pl.post.id = :postId")
    void deleteByPostId(@Param("postId") UUID postId);
}
