package com.devpick.domain.job.repository;

import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.entity.JobPostingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID>, JpaSpecificationExecutor<JobPosting> {

    interface TechTagFacetRow {
        String getName();
        Long getCount();
    }

    Optional<JobPosting> findBySourceUrl(String sourceUrl);

    @Query(value = """
            SELECT MIN(skill_name) AS name, COUNT(*) AS count
            FROM (
                SELECT job_posting_id, normalized_name, MIN(skill_name) AS skill_name
                FROM (
                    SELECT rs.job_posting_id,
                           lower(trim(rs.skill)) AS normalized_name,
                           trim(rs.skill) AS skill_name
                    FROM job_posting_required_skills rs
                    JOIN job_postings jp ON jp.id = rs.job_posting_id
                    WHERE rs.skill IS NOT NULL
                      AND trim(rs.skill) <> ''
                      AND NOT (
                          (jp.title IS NOT NULL AND length(lower(jp.title)) < 2)
                          OR (jp.company_name IS NOT NULL AND length(lower(jp.company_name)) < 2)
                          OR (jp.title IS NOT NULL AND lower(jp.title) LIKE '%더미%')
                          OR (jp.company_name IS NOT NULL AND lower(jp.company_name) LIKE '%더미%')
                      )
                    UNION ALL
                    SELECT ps.job_posting_id,
                           lower(trim(ps.skill)) AS normalized_name,
                           trim(ps.skill) AS skill_name
                    FROM job_posting_preferred_skills ps
                    JOIN job_postings jp ON jp.id = ps.job_posting_id
                    WHERE ps.skill IS NOT NULL
                      AND trim(ps.skill) <> ''
                      AND NOT (
                          (jp.title IS NOT NULL AND length(lower(jp.title)) < 2)
                          OR (jp.company_name IS NOT NULL AND length(lower(jp.company_name)) < 2)
                          OR (jp.title IS NOT NULL AND lower(jp.title) LIKE '%더미%')
                          OR (jp.company_name IS NOT NULL AND lower(jp.company_name) LIKE '%더미%')
                      )
                    UNION ALL
                    SELECT ts.job_posting_id,
                           lower(trim(ts.tech)) AS normalized_name,
                           trim(ts.tech) AS skill_name
                    FROM job_posting_tech_stack ts
                    JOIN job_postings jp ON jp.id = ts.job_posting_id
                    WHERE ts.tech IS NOT NULL
                      AND trim(ts.tech) <> ''
                      AND NOT (
                          (jp.title IS NOT NULL AND length(lower(jp.title)) < 2)
                          OR (jp.company_name IS NOT NULL AND length(lower(jp.company_name)) < 2)
                          OR (jp.title IS NOT NULL AND lower(jp.title) LIKE '%더미%')
                          OR (jp.company_name IS NOT NULL AND lower(jp.company_name) LIKE '%더미%')
                      )
                ) raw_tags
                GROUP BY job_posting_id, normalized_name
            ) posting_tags
            GROUP BY normalized_name
            ORDER BY COUNT(*) DESC, MIN(skill_name) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<TechTagFacetRow> findTopTechTagFacets(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE JobPosting j SET j.status = :expired " +
           "WHERE j.status = :active AND j.deadline IS NOT NULL AND j.deadline < :today")
    int expireActiveBefore(
            @Param("expired") JobPostingStatus expired,
            @Param("active") JobPostingStatus active,
            @Param("today") LocalDate today);
}
