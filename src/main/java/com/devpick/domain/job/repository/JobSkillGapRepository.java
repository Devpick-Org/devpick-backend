package com.devpick.domain.job.repository;

import com.devpick.domain.job.entity.JobSkillGap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobSkillGapRepository extends JpaRepository<JobSkillGap, UUID> {

    Optional<JobSkillGap> findByUserIdAndJobPosting_Id(UUID userId, UUID jobPostingId);

    @Query("SELECT g FROM JobSkillGap g JOIN FETCH g.jobPosting WHERE g.userId = :userId ORDER BY g.updatedAt DESC")
    List<JobSkillGap> findAllByUserIdWithPostingOrderByUpdatedAtDesc(@Param("userId") UUID userId);
}
