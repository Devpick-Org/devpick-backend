package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.AiQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AiQuestionRepository extends JpaRepository<AiQuestion, UUID> {

    Optional<AiQuestion> findByPost_Id(UUID postId);

    @Modifying
    @Query("DELETE FROM AiQuestion q WHERE q.post.id = :postId")
    void deleteByPostId(@Param("postId") UUID postId);
}
