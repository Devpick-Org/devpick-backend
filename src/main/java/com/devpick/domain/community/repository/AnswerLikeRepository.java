package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.AnswerLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AnswerLikeRepository extends JpaRepository<AnswerLike, UUID> {

    boolean existsByAnswer_IdAndUser_Id(UUID answerId, UUID userId);

    Optional<AnswerLike> findByAnswer_IdAndUser_Id(UUID answerId, UUID userId);

    long countByAnswer_Id(UUID answerId);

    @Modifying
    @Query("DELETE FROM AnswerLike al WHERE al.answer.id = :answerId")
    void deleteByAnswerId(@Param("answerId") UUID answerId);

    @Modifying
    @Query("DELETE FROM AnswerLike al WHERE al.answer.id IN (SELECT a.id FROM Answer a WHERE a.post.id = :postId)")
    void deleteByPostId(@Param("postId") UUID postId);
}
