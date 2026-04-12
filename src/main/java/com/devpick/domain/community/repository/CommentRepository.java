package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByAnswer_IdOrderByCreatedAtAsc(UUID answerId);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.answer.id = :answerId")
    void deleteByAnswerId(@Param("answerId") UUID answerId);

    @Modifying
    @Query("DELETE FROM Comment c WHERE c.answer.id IN (SELECT a.id FROM Answer a WHERE a.post.id = :postId)")
    void deleteByPostId(@Param("postId") UUID postId);
}
