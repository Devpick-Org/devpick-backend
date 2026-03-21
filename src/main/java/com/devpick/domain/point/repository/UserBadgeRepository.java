package com.devpick.domain.point.repository;

import com.devpick.domain.point.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {

    List<UserBadge> findByUser_IdOrderByAcquiredAtDesc(UUID userId);

    boolean existsByUser_IdAndBadge_Id(UUID userId, String badgeId);

    Optional<UserBadge> findTopByUser_IdOrderByAcquiredAtDesc(UUID userId);
}