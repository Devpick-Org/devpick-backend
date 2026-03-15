package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByAnswer_IdOrderByCreatedAtAsc(UUID answerId);
}
