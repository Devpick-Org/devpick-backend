package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    boolean existsByPost_IdAndUser_Id(UUID postId, UUID userId);

    Optional<PostLike> findByPost_IdAndUser_Id(UUID postId, UUID userId);

    long countByPost_Id(UUID postId);
}
