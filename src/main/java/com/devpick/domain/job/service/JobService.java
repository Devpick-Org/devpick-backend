package com.devpick.domain.job.service;

import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.job.client.JobAiClient;
import com.devpick.domain.job.dto.JobApiModels.CompanyFacetResponse;
import com.devpick.domain.job.dto.JobApiModels.ContentPickResponse;
import com.devpick.domain.job.dto.JobApiModels.JobBookmarkItemResponse;
import com.devpick.domain.job.dto.JobApiModels.JobBookmarkListResponse;
import com.devpick.domain.job.dto.JobApiModels.JobDetailResponse;
import com.devpick.domain.job.dto.JobApiModels.JobListItemResponse;
import com.devpick.domain.job.dto.JobApiModels.JobListPageResponse;
import com.devpick.domain.job.dto.JobApiModels.MatchBreakdownResponse;
import com.devpick.domain.job.dto.JobApiModels.MatchItemResponse;
import com.devpick.domain.job.dto.JobApiModels.MatchSubSectionResponse;
import com.devpick.domain.job.dto.JobApiModels.SkillGapResponse;
import com.devpick.domain.job.dto.JobApiModels.TechTagFacetResponse;
import com.devpick.domain.job.entity.JobBookmark;
import com.devpick.domain.job.entity.JobParseStatus;
import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.entity.JobPostingCategory;
import com.devpick.domain.job.entity.PostingExperienceLevel;
import com.devpick.domain.job.repository.JobBookmarkRepository;
import com.devpick.domain.job.repository.JobPostingRepository;
import com.devpick.domain.job.repository.JobPostingSpecifications;
import com.devpick.domain.resume.entity.MasterResume;
import com.devpick.domain.resume.repository.MasterResumeRepository;
import com.devpick.domain.resume.service.ResumeCryptoService;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.entity.UserTag;
import com.devpick.domain.user.repository.TagRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class JobService {

    public static final String LOGO_PLACEHOLDER = "https://placehold.co/64x64/png?text=Co";

    private static final int MATCH_SORT_CAP = 2000;
    private static final int TECH_TAG_FACET_LIMIT = 80;
    private static final String TECH_TAG_FACET_SOURCE = "JOB_POSTING";

    private final JobPostingRepository jobPostingRepository;
    private final JobBookmarkRepository jobBookmarkRepository;
    private final MasterResumeRepository masterResumeRepository;
    private final ResumeCryptoService resumeCryptoService;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final ContentRepository contentRepository;
    private final JobAiClient jobAiClient;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<TechTagFacetResponse> listTechTagFacets(Integer limit) {
        int boundedLimit = limit == null ? TECH_TAG_FACET_LIMIT : Math.max(1, Math.min(limit, 200));
        return jobPostingRepository.findTopTechTagFacets(boundedLimit).stream()
                .map(row -> new TechTagFacetResponse(
                        row.getName(),
                        row.getCount() != null ? row.getCount() : 0L,
                        TECH_TAG_FACET_SOURCE
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CompanyFacetResponse> listCompanyFacets(Integer limit) {
        int boundedLimit = limit == null ? TECH_TAG_FACET_LIMIT : Math.max(1, Math.min(limit, 200));
        return jobPostingRepository.findTopCompanyFacets(boundedLimit).stream()
                .map(row -> new CompanyFacetResponse(
                        row.getName(),
                        row.getCount() != null ? row.getCount() : 0L,
                        row.getLogoUrl()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public JobListPageResponse listJobs(
            UUID userId,
            int page,
            int size,
            String query,
            String category,
            String experienceLevel,
            String location,
            String techStackParam,
            String companiesParam,
            String sortBy
    ) {
        List<String> techStack = parseTechStackParam(techStackParam);
        List<String> companies = parseCsvParam(companiesParam);
        Specification<JobPosting> spec = Specification.allOf(
                JobPostingSpecifications.keyword(query),
                parseCategorySpec(category),
                parseExperienceSpec(experienceLevel),
                JobPostingSpecifications.locationContains(location),
                JobPostingSpecifications.companyIn(companies),
                JobPostingSpecifications.anyTechStack(techStack),
                JobPostingSpecifications.listableQuality()
        );

        Set<UUID> bookmarked = new HashSet<>(jobBookmarkRepository.findJobPostingIdsByUserId(userId));
        Map<String, Integer> userSkills = loadUserSkillProfile(userId);

        String sort = sortBy != null ? sortBy : "MATCH";
        if ("MATCH".equalsIgnoreCase(sort)) {
            List<JobPosting> all = jobPostingRepository.findAll(spec, PageRequest.of(0, MATCH_SORT_CAP)).getContent();
            List<ScoredPosting> scored = all.stream()
                    .map(p -> new ScoredPosting(p, JobMatchingCalculator.compute(p, userSkills)))
                    .sorted(Comparator.comparingInt((ScoredPosting s) -> s.match().matchScore()).reversed())
                    .toList();
            long total = scored.size();
            int totalPages = (int) Math.ceil(total / (double) size);
            int from = page * size;
            List<JobPosting> slice = scored.stream()
                    .skip(from)
                    .limit(size)
                    .map(ScoredPosting::posting)
                    .toList();
            List<JobListItemResponse> items = slice.stream()
                    .map(p -> toListItem(p, userSkills, bookmarked.contains(p.getId())))
                    .toList();
            return new JobListPageResponse(items, total, Math.max(1, totalPages), page, size);
        }

        if ("DEADLINE".equalsIgnoreCase(sort)) {
            List<JobPosting> all = jobPostingRepository.findAll(spec, PageRequest.of(0, MATCH_SORT_CAP)).getContent();
            List<JobPosting> sorted = all.stream().sorted(deadlineImminentOrder()).toList();
            long total = sorted.size();
            int totalPages = (int) Math.ceil(total / (double) size);
            int from = page * size;
            List<JobPosting> slice = sorted.stream().skip(from).limit(size).toList();
            List<JobListItemResponse> items = slice.stream()
                    .map(p -> toListItem(p, userSkills, bookmarked.contains(p.getId())))
                    .toList();
            return new JobListPageResponse(items, total, Math.max(1, totalPages), page, size);
        }

        Sort jpaSort = Sort.by(Sort.Order.desc("createdAt"));
        Page<JobPosting> result = jobPostingRepository.findAll(spec, PageRequest.of(page, size, jpaSort));
        List<JobListItemResponse> items = result.getContent().stream()
                .map(p -> toListItem(p, userSkills, bookmarked.contains(p.getId())))
                .toList();
        return new JobListPageResponse(
                items,
                result.getTotalElements(),
                result.getTotalPages(),
                page,
                size
        );
    }

    /**
     * 마감일이 있는 공고를 오름차순(임박 순), 마감일 없음은 맨 뒤. 동일 시 최신 수집 순.
     * JPA Sort + deadline nullsLast + Specification 조합에서 빈 페이지가 나오는 이슈를 피하기 위해 인메모리 정렬한다.
     */
    private static Comparator<JobPosting> deadlineImminentOrder() {
        return Comparator
                .comparing((JobPosting p) -> p.getDeadline() != null ? p.getDeadline() : LocalDate.MAX)
                .thenComparing(JobPosting::getCreatedAt, Comparator.reverseOrder());
    }

    private record ScoredPosting(JobPosting posting, JobMatchingCalculator.MatchResult match) {}

    private Specification<JobPosting> parseCategorySpec(String category) {
        if (category == null || "ALL".equalsIgnoreCase(category)) {
            return (root, q, cb) -> cb.conjunction();
        }
        try {
            JobPostingCategory c = JobPostingCategory.valueOf(category.trim().toUpperCase(Locale.ROOT));
            return JobPostingSpecifications.category(c);
        } catch (IllegalArgumentException e) {
            return (root, q, cb) -> cb.conjunction();
        }
    }

    private List<String> parseTechStackParam(String raw) {
        return parseCsvParam(raw);
    }

    private List<String> parseCsvParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private Specification<JobPosting> parseExperienceSpec(String level) {
        if (level == null || "ALL".equalsIgnoreCase(level)) {
            return (root, q, cb) -> cb.conjunction();
        }
        try {
            PostingExperienceLevel e = PostingExperienceLevel.valueOf(level.trim().toUpperCase(Locale.ROOT));
            return JobPostingSpecifications.experience(e);
        } catch (IllegalArgumentException e) {
            return (root, q, cb) -> cb.conjunction();
        }
    }

    private void ensureListableJob(JobPosting p) {
        if (!JobPostingSpecifications.passesListableQuality(p.getTitle(), p.getCompanyName())) {
            throw new DevpickException(ErrorCode.JOB_NOT_FOUND);
        }
    }

    /** 면접 Q&A 목록 등에서 카드용 매칭 점수 계산 */
    @Transactional(readOnly = true)
    public int computeMatchScoreForJob(UUID userId, UUID jobId) {
        JobPosting p = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new DevpickException(ErrorCode.JOB_NOT_FOUND));
        ensureListableJob(p);
        Map<String, Integer> userSkills = loadUserSkillProfile(userId);
        return JobMatchingCalculator.compute(p, userSkills).matchScore();
    }

    /** 배치·운영 점검: 로그인 사용자 없이 동일 스냅샷(매칭·북마크는 비로그인 기준). */
    @Transactional(readOnly = true)
    public JobDetailResponse getJobDetailForInternalOps(UUID jobId) {
        return getJobDetail(INTERNAL_OPS_USER_ID, jobId);
    }

    private static final UUID INTERNAL_OPS_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Transactional(readOnly = true)
    public JobDetailResponse getJobDetail(UUID userId, UUID jobId) {
        JobPosting p = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new DevpickException(ErrorCode.JOB_NOT_FOUND));
        ensureListableJob(p);
        boolean bookmarked = jobBookmarkRepository.existsByUserIdAndJobPosting_Id(userId, jobId);
        Map<String, Integer> userSkills = loadUserSkillProfile(userId);
        JsonNode resumeRoot = loadResumeJson(userId);
        int careerYears = JobMatchingCalculator.careerYearsFromResume(resumeRoot);
        MatchBreakdownResponse breakdown = buildMatchBreakdown(p, userSkills, careerYears);
        JobListItemResponse base = toListItem(p, userSkills, bookmarked);
        return new JobDetailResponse(
                base.id(),
                base.companyName(),
                base.companyLogo(),
                base.title(),
                base.employmentType(),
                base.jobCategory(),
                base.experienceLevel(),
                base.location(),
                base.deadline(),
                base.techStack(),
                base.matchScore(),
                base.matchedTags(),
                base.missingTags(),
                base.bookmarked(),
                base.status(),
                p.getSalaryDisplay() != null ? p.getSalaryDisplay() : "",
                p.getApplyUrl() != null ? p.getApplyUrl() : p.getSourceUrl(),
                new ArrayList<>(p.getResponsibilities()),
                new ArrayList<>(p.getRequirementBullets().isEmpty() ? p.getRequiredSkills() : p.getRequirementBullets()),
                new ArrayList<>(p.getPreferredQualificationBullets().isEmpty() ? p.getPreferredSkills() : p.getPreferredQualificationBullets()),
                new ArrayList<>(p.getBenefits()),
                new ArrayList<>(p.getHiringProcess()),
                new ArrayList<>(p.getJdImageUrls() != null ? p.getJdImageUrls() : List.of()),
                p.getParseStatus() != null ? p.getParseStatus().name() : JobParseStatus.PENDING.name(),
                breakdown
        );
    }

    @Transactional
    public void bookmark(UUID userId, UUID jobId) {
        JobPosting p = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new DevpickException(ErrorCode.JOB_NOT_FOUND));
        ensureListableJob(p);
        if (jobBookmarkRepository.existsByUserIdAndJobPosting_Id(userId, jobId)) {
            return;
        }
        jobBookmarkRepository.save(com.devpick.domain.job.entity.JobBookmark.builder()
                .userId(userId)
                .jobPosting(p)
                .build());
    }

    @Transactional
    public void unbookmark(UUID userId, UUID jobId) {
        if (!jobBookmarkRepository.existsByUserIdAndJobPosting_Id(userId, jobId)) {
            throw new DevpickException(ErrorCode.JOB_BOOKMARK_NOT_FOUND);
        }
        jobBookmarkRepository.deleteByUserIdAndJobPosting_Id(userId, jobId);
    }

    @Transactional(readOnly = true)
    public JobBookmarkListResponse getBookmarkedJobs(UUID userId, Pageable pageable) {
        Page<JobBookmark> page = jobBookmarkRepository.findByUserIdWithPosting(userId, pageable);
        List<JobBookmarkItemResponse> items = page.getContent().stream()
                .map(this::toBookmarkItem)
                .toList();
        return new JobBookmarkListResponse(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private JobBookmarkItemResponse toBookmarkItem(JobBookmark b) {
        JobPosting p = b.getJobPosting();
        String logo = p.getCompanyLogoUrl() != null && !p.getCompanyLogoUrl().isBlank()
                ? p.getCompanyLogoUrl()
                : LOGO_PLACEHOLDER;
        return new JobBookmarkItemResponse(
                p.getId(),
                p.getCompanyName(),
                logo,
                p.getTitle(),
                p.getEmploymentType().name(),
                p.getExperienceLevel().name(),
                p.getLocation() != null ? p.getLocation() : "",
                formatDeadlineLabel(p),
                new ArrayList<>(p.getTechStack()),
                b.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    @Transactional(readOnly = true)
    public SkillGapResponse skillGap(UUID userId, UUID jobId) {
        JobPosting p = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new DevpickException(ErrorCode.JOB_NOT_FOUND));
        ensureListableJob(p);
        Map<String, Integer> userSkills = loadUserSkillProfile(userId);
        List<String> missing = p.getRequiredSkills().stream()
                .filter(s -> !skillMet(userSkills, s))
                .toList();
        JsonNode resume = loadResumeJson(userId);
        String resumeJson = resume != null ? resume.toString() : "{}";
        Map<String, Object> body = new HashMap<>();
        body.put("missing_skills", missing);
        body.put("job_title", p.getTitle());
        body.put("company_name", p.getCompanyName());
        body.put("resume_json", resumeJson);
        Map<String, Object> ai = jobAiClient.skillGap(body);
        @SuppressWarnings("unchecked")
        List<String> roadmap = (List<String>) ai.getOrDefault("roadmap", List.of());

        List<ContentPickResponse> picks = recommendContents(missing);
        return new SkillGapResponse(roadmap, picks);
    }

    private List<ContentPickResponse> recommendContents(List<String> missingSkills) {
        if (missingSkills.isEmpty()) {
            return List.of();
        }
        List<Tag> tags = tagRepository.findByNameIgnoreCaseIn(missingSkills.stream().map(String::trim).toList());
        List<UUID> tagIds = tags.stream().map(Tag::getId).toList();
        if (tagIds.isEmpty()) {
            Page<Content> page = contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(PageRequest.of(0, 3));
            return page.getContent().stream().map(this::toContentPick).toList();
        }
        Page<Content> page = contentRepository.findByTagIdsAndIsAvailableTrue(tagIds, PageRequest.of(0, 3));
        return page.getContent().stream().map(this::toContentPick).toList();
    }

    private ContentPickResponse toContentPick(Content c) {
        List<String> tagNames = c.getContentTags() == null ? List.of() : c.getContentTags().stream()
                .map(ct -> ct.getTag().getName())
                .toList();
        return new ContentPickResponse(
                c.getId().toString(),
                c.getTitle(),
                c.getPreview(),
                c.getCanonicalUrl(),
                tagNames
        );
    }

    private MatchBreakdownResponse buildMatchBreakdown(JobPosting p, Map<String, Integer> userSkills, int careerYears) {
        List<MatchItemResponse> reqItems = p.getRequiredSkills().stream()
                .map(skill -> new MatchItemResponse(skill, skillMet(userSkills, skill) ? "MET" : "UNMET"))
                .toList();
        long reqMet = reqItems.stream().filter(i -> "MET".equals(i.status())).count();
        int reqScore = p.getRequiredSkills().isEmpty() ? 100 : (int) Math.round(100.0 * reqMet / p.getRequiredSkills().size());

        final List<MatchItemResponse> prefItems;
        final int prefScore;
        final String prefSummary;
        if (p.getPreferredSkills().isEmpty()) {
            prefScore = 0;
            prefSummary =
                    "매칭용 우대 스킬 태그가 공고 데이터에 없어요. 본문 우대 조건 문구와 이력서를 직접 비교해 주세요.";
            prefItems =
                    List.of(new MatchItemResponse("(우대 스킬 목록 미등록 — 상세 페이지 본문 확인)", "PARTIAL"));
        } else {
            prefItems = p.getPreferredSkills().stream()
                    .map(skill ->
                            new MatchItemResponse(skill, skillMet(userSkills, skill) ? "MET" : "UNMET"))
                    .toList();
            long prefMet = prefItems.stream().filter(i -> "MET".equals(i.status())).count();
            prefSummary = "우대 기술 매칭";
            prefScore = (int) Math.round(100.0 * prefMet / p.getPreferredSkills().size());
        }

        int expMet = JobMatchingCalculator.experienceScoreMet(p.getExperienceLevel(), careerYears);
        List<MatchItemResponse> expItems = List.of(
                new MatchItemResponse(
                        "경력 요건 (" + p.getExperienceLevel() + ")",
                        expMet == 1 ? "MET" : "UNMET"
                )
        );

        return new MatchBreakdownResponse(
                new MatchSubSectionResponse(reqScore, 100, "필수 기술 매칭", reqItems),
                new MatchSubSectionResponse(
                        prefScore,
                        p.getPreferredSkills().isEmpty() ? 0 : 100,
                        prefSummary,
                        prefItems),
                new MatchSubSectionResponse(expMet * 100, 100, "경력 수준", expItems)
        );
    }

    private boolean skillMet(Map<String, Integer> userSkills, String skill) {
        if (userSkills == null || userSkills.isEmpty()) {
            return false;
        }
        if (skill == null || skill.isBlank()) {
            return false;
        }
        String k = JobSkillNormalizer.canonicalLower(skill.trim());
        if (k.isEmpty()) {
            return false;
        }
        if (userSkills.containsKey(k)) {
            return true;
        }
        for (String u : userSkills.keySet()) {
            if (u.contains(k) || k.contains(u)) {
                return true;
            }
        }
        return false;
    }

    /** API 문자열: 고정일(yyyy-MM-dd) | 상시(채용 시 마감) | 미정(빈 문자열). */
    private static String formatDeadlineLabel(JobPosting p) {
        if (Boolean.TRUE.equals(p.getRollingDeadline())) {
            return "채용 시 마감";
        }
        if (p.getDeadline() != null) {
            return p.getDeadline().toString();
        }
        return "";
    }

    private JobListItemResponse toListItem(JobPosting p, Map<String, Integer> userSkills, boolean bookmarked) {
        JobMatchingCalculator.MatchResult m = JobMatchingCalculator.compute(p, userSkills);
        String deadline = formatDeadlineLabel(p);
        List<String> tech = p.getTechStack().isEmpty()
                ? combineSkills(p)
                : new ArrayList<>(p.getTechStack());
        String logo = p.getCompanyLogoUrl() != null && !p.getCompanyLogoUrl().isBlank()
                ? p.getCompanyLogoUrl()
                : LOGO_PLACEHOLDER;
        return new JobListItemResponse(
                p.getId().toString(),
                p.getCompanyName(),
                logo,
                p.getTitle(),
                p.getEmploymentType().name(),
                p.getJobCategory().name(),
                p.getExperienceLevel().name(),
                p.getLocation() != null ? p.getLocation() : "",
                deadline,
                tech,
                m.matchScore(),
                m.matchedTags(),
                m.missingTags(),
                bookmarked,
                p.getStatus().name()
        );
    }

    private List<String> combineSkills(JobPosting p) {
        List<String> out = new ArrayList<>();
        out.addAll(p.getRequiredSkills());
        out.addAll(p.getPreferredSkills());
        return out.stream().distinct().toList();
    }

    private Map<String, Integer> loadUserSkillProfile(UUID userId) {
        Map<String, Integer> map = new HashMap<>();
        JsonNode resume = loadResumeJson(userId);
        map.putAll(JobMatchingCalculator.skillsFromResumeJson(resume));
        userRepository.findById(userId).ifPresent(u -> {
            for (UserTag ut : u.getUserTags()) {
                String name = ut.getTag().getName();
                map.putIfAbsent(name.toLowerCase(Locale.ROOT), 45);
            }
        });
        return JobMatchingCalculator.normalizeSkillMap(map);
    }

    private JsonNode loadResumeJson(UUID userId) {
        return masterResumeRepository.findByUserId(userId)
                .map(m -> {
                    try {
                        String json = resumeCryptoService.decrypt(m.getEncryptedPayload());
                        return objectMapper.readTree(json);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(null);
    }
}
