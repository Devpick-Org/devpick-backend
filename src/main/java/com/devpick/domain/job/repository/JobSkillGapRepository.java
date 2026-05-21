package com.devpick.domain.job.repository;

import com.devpick.domain.job.entity.JobSkillGap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JobSkillGapRepository extends JpaRepository<JobSkillGap, UUID> {

    Optional<JobSkillGap> findByUserIdAndJobPosting_Id(UUID userId, UUID jobPostingId);
}
