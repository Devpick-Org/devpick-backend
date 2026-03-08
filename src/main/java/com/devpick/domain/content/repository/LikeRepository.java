package com.devpick.domain.content.repository;

import com.devpick.domain.content.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LikeRepository extends JpaRepository<Like, UUID> {

    boolean existsByUser_IdAndContent_Id(UUID userId, UUID contentId);

    Optional<Like> findByUser_IdAndContent_Id(UUID userId, UUID contentId);
}
