package com.devpick.domain.job.repository;

import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.entity.JobPostingCategory;
import com.devpick.domain.job.entity.JobPostingStatus;
import com.devpick.domain.job.entity.PostingExperienceLevel;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Locale;

public final class JobPostingSpecifications {

    private JobPostingSpecifications() {
    }

    public static Specification<JobPosting> keyword(String q) {
        if (q == null || q.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("companyName")), pattern)
        );
    }

    public static Specification<JobPosting> category(JobPostingCategory category) {
        if (category == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("jobCategory"), category);
    }

    public static Specification<JobPosting> experience(PostingExperienceLevel level) {
        if (level == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("experienceLevel"), level);
    }

    public static Specification<JobPosting> locationContains(String location) {
        if (location == null || location.isBlank() || "ALL".equalsIgnoreCase(location)) {
            return (root, query, cb) -> cb.conjunction();
        }
        String pattern = "%" + location.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("location")), pattern);
    }

    public static Specification<JobPosting> anyTechStack(List<String> techStack) {
        if (techStack == null || techStack.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        List<String> normalized = techStack.stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .toList();
        if (normalized.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        Specification<JobPosting> reqSpec = (root, query, cb) -> {
            query.distinct(true);
            return root.join("requiredSkills").in(normalized);
        };
        Specification<JobPosting> prefSpec = (root, query, cb) -> {
            query.distinct(true);
            return root.join("preferredSkills").in(normalized);
        };
        Specification<JobPosting> techSpec = (root, query, cb) -> {
            query.distinct(true);
            return root.join("techStack").in(normalized);
        };
        return reqSpec.or(prefSpec).or(techSpec);
    }

    /** 목록: 활성 우선 노출용 — 만료도 포함(기획: 만료 공고 접근 허용). 필터로 상태 좁힐 때 사용. */
    public static Specification<JobPosting> status(JobPostingStatus status) {
        if (status == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
