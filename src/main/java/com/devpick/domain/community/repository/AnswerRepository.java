package com.devpick.domain.community.repository;

import com.devpick.domain.community.entity.Answer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AnswerRepository extends JpaRepository<Answer, UUID> {

    long countByPost_Id(UUID postId);

    List<Answer> findByPost_IdOrderByCreatedAtAsc(UUID postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Answer a WHERE a.post.id = :postId AND a.isAdopted = true")
    List<Answer> findAdoptedByPostIdForUpdate(@Param("postId") UUID postId);
}
