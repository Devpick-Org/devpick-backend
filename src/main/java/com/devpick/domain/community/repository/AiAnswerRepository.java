package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.AiAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiAnswerRepository extends JpaRepository<AiAnswer, UUID> {

    Optional<AiAnswer> findByPost_Id(UUID postId);
}
