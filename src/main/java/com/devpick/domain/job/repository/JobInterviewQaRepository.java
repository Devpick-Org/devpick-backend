package com.devpick.domain.job.repository;

import com.devpick.domain.job.entity.JobInterviewQa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobInterviewQaRepository extends JpaRepository<JobInterviewQa, UUID> {

    @Query("SELECT q FROM JobInterviewQa q JOIN FETCH q.jobPosting WHERE q.userId = :userId ORDER BY q.updatedAt DESC")
    List<JobInterviewQa> findAllByUserIdWithPostingOrderByUpdatedAtDesc(@Param("userId") UUID userId);

    Optional<JobInterviewQa> findByUserIdAndJobPosting_Id(UUID userId, UUID jobPostingId);

    void deleteByUserIdAndJobPosting_Id(UUID userId, UUID jobPostingId);
}
