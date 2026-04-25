package com.devpick.domain.job.repository;

import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.entity.JobPostingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID>, JpaSpecificationExecutor<JobPosting> {

    Optional<JobPosting> findBySourceUrl(String sourceUrl);

    @Modifying
    @Query("UPDATE JobPosting j SET j.status = :expired " +
           "WHERE j.status = :active AND j.deadline IS NOT NULL AND j.deadline < :today")
    int expireActiveBefore(
            @Param("expired") JobPostingStatus expired,
            @Param("active") JobPostingStatus active,
            @Param("today") LocalDate today);
}
