package com.devpick.domain.job.repository;

import com.devpick.domain.job.entity.MockInterviewSession;
import com.devpick.domain.job.entity.MockInterviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MockInterviewSessionRepository extends JpaRepository<MockInterviewSession, UUID> {

    @Query("SELECT s FROM MockInterviewSession s "
            + "LEFT JOIN FETCH s.jobPosting "
            + "WHERE s.userId = :userId "
            + "ORDER BY s.updatedAt DESC")
    List<MockInterviewSession> findAllByUserIdWithJobOrderByUpdatedAtDesc(@Param("userId") UUID userId);

    Optional<MockInterviewSession> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, MockInterviewStatus status);

    @Query("SELECT s FROM MockInterviewSession s "
            + "WHERE s.userId = :userId AND s.status = :status "
            + "ORDER BY s.updatedAt ASC")
    List<MockInterviewSession> findOldestByUserIdAndStatus(
            @Param("userId") UUID userId, @Param("status") MockInterviewStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM MockInterviewSession s WHERE s.id = :id")
    Optional<MockInterviewSession> findByIdForUpdate(@Param("id") UUID id);
}
