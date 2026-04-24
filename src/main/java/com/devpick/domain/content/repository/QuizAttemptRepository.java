package com.devpick.domain.content.repository;

import com.devpick.domain.content.entity.QuizAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    Optional<QuizAttempt> findTopByUser_IdAndContent_IdOrderByCreatedAtDesc(UUID userId, UUID contentId);

    @Query(value = "SELECT qa FROM QuizAttempt qa JOIN FETCH qa.content c JOIN FETCH c.source " +
                   "WHERE qa.user.id = :userId " +
                   "AND qa.createdAt = (SELECT MAX(qa2.createdAt) FROM QuizAttempt qa2 " +
                   "WHERE qa2.user.id = :userId AND qa2.content.id = qa.content.id AND qa2.level = qa.level) " +
                   "AND qa.score < qa.totalQuestions",
           countQuery = "SELECT COUNT(qa) FROM QuizAttempt qa " +
                        "WHERE qa.user.id = :userId " +
                        "AND qa.createdAt = (SELECT MAX(qa2.createdAt) FROM QuizAttempt qa2 " +
                        "WHERE qa2.user.id = :userId AND qa2.content.id = qa.content.id AND qa2.level = qa.level) " +
                        "AND qa.score < qa.totalQuestions")
    Page<QuizAttempt> findHistoryByUserId(@Param("userId") UUID userId, Pageable pageable);

    Optional<QuizAttempt> findByIdAndUser_Id(UUID id, UUID userId);
}
