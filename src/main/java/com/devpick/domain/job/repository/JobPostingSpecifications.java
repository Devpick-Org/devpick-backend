package com.devpick.domain.job.repository;

import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.entity.JobPostingCategory;
import com.devpick.domain.job.entity.JobPostingStatus;
import com.devpick.domain.job.entity.PostingExperienceLevel;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
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
        if (companyName != null && companyName.toLowerCase(lc).contains("더미")) {
            return false;
        }
        if (title != null && title.toLowerCase(lc).contains("(sample)")) {
            return false;
        }
        return companyName == null || !companyName.toLowerCase(lc).contains("테스트원");
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

    public static Specification<JobPosting> companyIn(List<String> companies) {
        if (companies == null || companies.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        List<String> normalized = companies.stream()
                .map(s -> s == null ? "" : s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.lower(root.get("companyName")).in(normalized);
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
        // OR 로 여러 join 을 묶으면 동일 공고 행이 SQL 에서 중복될 수 있다.
        // EXISTS 서브쿼리로 필터만 걸어 중복 없이 매칭한다.
        return (root, query, cb) -> {
            List<Predicate> skillOr = new ArrayList<>();
            for (String skill : normalized) {
                Subquery<Long> sqReq = query.subquery(Long.class);
                Root<JobPosting> rReq = sqReq.from(JobPosting.class);
                Join<JobPosting, String> jReq = rReq.join("requiredSkills", JoinType.INNER);
                sqReq.select(cb.literal(1L)).where(cb.and(
                        cb.equal(rReq.get("id"), root.get("id")),
                        cb.equal(jReq, skill)));

                Subquery<Long> sqPref = query.subquery(Long.class);
                Root<JobPosting> rPref = sqPref.from(JobPosting.class);
                Join<JobPosting, String> jPref = rPref.join("preferredSkills", JoinType.INNER);
                sqPref.select(cb.literal(1L)).where(cb.and(
                        cb.equal(rPref.get("id"), root.get("id")),
                        cb.equal(jPref, skill)));

                Subquery<Long> sqTech = query.subquery(Long.class);
                Root<JobPosting> rTech = sqTech.from(JobPosting.class);
                Join<JobPosting, String> jTech = rTech.join("techStack", JoinType.INNER);
                sqTech.select(cb.literal(1L)).where(cb.and(
                        cb.equal(rTech.get("id"), root.get("id")),
                        cb.equal(jTech, skill)));

                skillOr.add(cb.or(
                        cb.exists(sqReq),
                        cb.exists(sqPref),
                        cb.exists(sqTech)));
            }
            return cb.or(skillOr.toArray(Predicate[]::new));
        };
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
            Predicate sampleInTitle = cb.and(cb.isNotNull(title), cb.like(cb.lower(title), "%(sample)%"));
            Predicate testCompany = cb.and(cb.isNotNull(company), cb.like(cb.lower(company), "%테스트원%"));
            Predicate bad = cb.or(
                    titleTooShort, companyTooShort, dummyInTitle, dummyInCompany, sampleInTitle, testCompany);
            return cb.not(bad);
        };
    }

    @SuppressWarnings("unchecked")
    private static Expression<String> stringPath(jakarta.persistence.criteria.Root<?> root, String attribute) {
        return (Expression<String>) (Expression<?>) root.get(attribute);
    }
}
