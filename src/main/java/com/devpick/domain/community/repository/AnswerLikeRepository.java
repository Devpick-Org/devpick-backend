package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.AnswerLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AnswerLikeRepository extends JpaRepository<AnswerLike, UUID> {

    boolean existsByAnswer_IdAndUser_Id(UUID answerId, UUID userId);

    Optional<AnswerLike> findByAnswer_IdAndUser_Id(UUID answerId, UUID userId);

    long countByAnswer_Id(UUID answerId);
}
