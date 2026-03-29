package com.devpick.domain.content.repository;

import com.devpick.domain.content.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    Optional<QuizAttempt> findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(UUID userId, UUID contentId);
}
