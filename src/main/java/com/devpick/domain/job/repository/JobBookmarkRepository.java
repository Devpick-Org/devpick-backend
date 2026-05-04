package com.devpick.domain.job.repository;

import com.devpick.domain.job.entity.JobBookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobBookmarkRepository extends JpaRepository<JobBookmark, UUID> {

    @Query("select b.jobPosting.id from JobBookmark b where b.userId = :userId")
    List<UUID> findJobPostingIdsByUserId(@Param("userId") UUID userId);

    @Query(value = "select b from JobBookmark b join fetch b.jobPosting where b.userId = :userId",
           countQuery = "select count(b) from JobBookmark b where b.userId = :userId")
    Page<JobBookmark> findByUserIdWithPosting(@Param("userId") UUID userId, Pageable pageable);

    boolean existsByUserIdAndJobPosting_Id(UUID userId, UUID jobPostingId);

    Optional<JobBookmark> findByUserIdAndJobPosting_Id(UUID userId, UUID jobPostingId);

    void deleteByUserIdAndJobPosting_Id(UUID userId, UUID jobPostingId);
}
