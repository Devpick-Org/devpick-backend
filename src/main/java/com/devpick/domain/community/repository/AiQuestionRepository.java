package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.AiQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiQuestionRepository extends JpaRepository<AiQuestion, UUID> {

    Optional<AiQuestion> findByPost_Id(UUID postId);
}
