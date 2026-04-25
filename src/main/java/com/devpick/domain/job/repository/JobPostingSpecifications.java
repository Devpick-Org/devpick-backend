package com.devpick.domain.job.repository;

import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.entity.JobPostingCategory;
import com.devpick.domain.job.entity.JobPostingStatus;
import com.devpick.domain.job.entity.PostingExperienceLevel;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.List;
import java.util.Locale;

public final class JobPostingSpecifications {

    private JobPostingSpecifications() {
    }

    /**
     * {@link #listableQuality()}와 동일한 기준(코드포인트 길이 기준). 목록용 Specification과
     * 단건 조회 거부 로직을 맞추기 위해 사용한다.
     */
    public static boolean passesListableQuality(String title, String companyName) {
        Locale lc = Locale.ROOT;
        if (title != null && title.toLowerCase(lc).length() < 2) {
            return false;
        }
        if (companyName != null && companyName.toLowerCase(lc).length() < 2) {
            return false;
        }
        if (title != null && title.toLowerCase(lc).contains("더미")) {
            return false;
        }
        return companyName == null || !companyName.toLowerCase(lc).contains("더미");
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

    /**
     * 테스트/수집 실패로 회사명·제목이 한 글자이거나 "더미"가 들어간 행은 목록에서 제외한다.
     */
    public static Specification<JobPosting> listableQuality() {
        return (root, query, cb) -> {
            Expression<String> title = stringPath(root, "title");
            Expression<String> company = stringPath(root, "companyName");
            Predicate titleTooShort = cb.and(
                    cb.isNotNull(title),
                    cb.lessThan(cb.length(cb.lower(title)), 2));
            Predicate companyTooShort = cb.and(
                    cb.isNotNull(company),
                    cb.lessThan(cb.length(cb.lower(company)), 2));
            Predicate dummyInTitle = cb.and(cb.isNotNull(title), cb.like(cb.lower(title), "%더미%"));
            Predicate dummyInCompany = cb.and(cb.isNotNull(company), cb.like(cb.lower(company), "%더미%"));
            Predicate bad = cb.or(titleTooShort, companyTooShort, dummyInTitle, dummyInCompany);
            return cb.not(bad);
        };
    }

    @SuppressWarnings("unchecked")
    private static Expression<String> stringPath(jakarta.persistence.criteria.Root<?> root, String attribute) {
        return (Expression<String>) (Expression<?>) root.get(attribute);
    }
}
