package com.devpick.domain.content.repository;

import com.devpick.domain.content.entity.QuizAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, UUID> {

    List<QuizAttemptAnswer> findByAttempt_Id(UUID attemptId);
}
