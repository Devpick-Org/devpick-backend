package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.AiAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AiAnswerRepository extends JpaRepository<AiAnswer, UUID> {

    Optional<AiAnswer> findByPost_Id(UUID postId);

    @Modifying
    @Query("DELETE FROM AiAnswer a WHERE a.post.id = :postId")
    void deleteByPostId(@Param("postId") UUID postId);
}
