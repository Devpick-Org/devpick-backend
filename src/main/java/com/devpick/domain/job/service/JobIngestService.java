package com.devpick.domain.job.service;

import com.devpick.domain.job.client.JobAiClient;
import com.devpick.domain.job.client.JobAiClient.JobJdParseResult;
import com.devpick.domain.job.dto.JobApiModels.JobIngestRequest;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.domain.job.entity.EmploymentType;
import com.devpick.domain.job.entity.JobParseStatus;
import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.entity.JobPostingCategory;
import com.devpick.domain.job.entity.JobPostingStatus;
import com.devpick.domain.job.entity.JobSource;
import com.devpick.domain.job.entity.PostingExperienceLevel;
import com.devpick.domain.job.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JobIngestService {

    private final JobPostingRepository jobPostingRepository;
    private final JobAiClient jobAiClient;

    @Transactional
    public JobPosting ingest(JobIngestRequest req) {
        if (req.sourceUrl() == null || req.sourceUrl().isBlank()) {
            throw new IllegalArgumentException("sourceUrl required");
        }
        String url = req.sourceUrl().trim();
        JobPosting posting = jobPostingRepository.findBySourceUrl(url)
                .orElseGet(() -> {
                    JobPosting p = new JobPosting();
                    p.setSource(JobSource.RALLIT);
                    p.setSourceUrl(url);
                    p.setTitle("");
                    p.setCompanyName("");
                    p.setEmploymentType(EmploymentType.FULL_TIME);
                    p.setJobCategory(JobPostingCategory.BACKEND);
                    p.setExperienceLevel(PostingExperienceLevel.ANY);
                    p.setStatus(JobPostingStatus.ACTIVE);
                    p.setParseStatus(JobParseStatus.PENDING);
                    p.setRequiredSkills(new ArrayList<>());
                    p.setPreferredSkills(new ArrayList<>());
                    p.setTechStack(new ArrayList<>());
                    p.setResponsibilities(new ArrayList<>());
                    p.setRequirementBullets(new ArrayList<>());
                    p.setPreferredQualificationBullets(new ArrayList<>());
                    p.setBenefits(new ArrayList<>());
                    p.setHiringProcess(new ArrayList<>());
                    p.setJdImageUrls(new ArrayList<>());
                    return p;
                });

        applyMetadata(posting, req);

        if (Boolean.TRUE.equals(req.imageOnlyJd())) {
            posting.setParseStatus(JobParseStatus.SKIPPED_IMAGE);
            applyIngestSkillListsWithoutChangingParseStatus(posting, req);
            return jobPostingRepository.save(posting);
        }

        if (req.rawJdText() != null && !req.rawJdText().isBlank()) {
            JobJdParseResult parsed;
            try {
                parsed = jobAiClient.parseJd(req.rawJdText());
            } catch (DevpickException e) {
                if (applySkillHintsFallback(posting, req)) {
                    return jobPostingRepository.save(posting);
                }
                throw e;
            }
            if (parsed.skipReason() != null && "image_jd".equals(parsed.skipReason())) {
                posting.setParseStatus(JobParseStatus.SKIPPED_IMAGE);
            } else if ((parsed.requiredSkills() == null || parsed.requiredSkills().isEmpty())
                    && (parsed.preferredSkills() == null || parsed.preferredSkills().isEmpty())) {
                if (!applySkillHintsFallback(posting, req)) {
                    posting.setParseStatus(JobParseStatus.UNPARSABLE);
                }
            } else {
                posting.applyParsedSkills(
                        parsed.requiredSkills() != null ? parsed.requiredSkills() : List.of(),
                        parsed.preferredSkills() != null ? parsed.preferredSkills() : List.of());
            }
            if (posting.getParseStatus() == JobParseStatus.SKIPPED_IMAGE) {
                applyIngestSkillListsWithoutChangingParseStatus(posting, req);
            }
            return jobPostingRepository.save(posting);
        }

        if ((req.requiredSkills() != null && !req.requiredSkills().isEmpty())
                || (req.preferredSkills() != null && !req.preferredSkills().isEmpty())) {
            posting.applyParsedSkills(
                    req.requiredSkills() != null ? req.requiredSkills() : List.of(),
                    req.preferredSkills() != null ? req.preferredSkills() : List.of());
            return jobPostingRepository.save(posting);
        }

        posting.setParseStatus(JobParseStatus.PENDING);
        return jobPostingRepository.save(posting);
    }

    /**
     * AI JD 파싱이 비었거나 AI 호출에 실패했을 때, ingest 요청에 실린 기술 힌트로 스킬을 채웁니다.
     *
     * @return 힌트가 있어 {@link JobPosting#applyParsedSkills}를 적용했으면 true
     */
    private boolean applySkillHintsFallback(JobPosting posting, JobIngestRequest req) {
        List<String> fbReq = req.requiredSkills() != null ? req.requiredSkills() : List.of();
        List<String> fbPref = req.preferredSkills() != null ? req.preferredSkills() : List.of();
        if (fbReq.isEmpty() && fbPref.isEmpty()) {
            return false;
        }
        posting.applyParsedSkills(fbReq, fbPref);
        return true;
    }

    private void applyMetadata(JobPosting posting, JobIngestRequest req) {
        if (req.companyName() != null) {
            posting.setCompanyName(req.companyName());
        }
        if (req.companyLogoUrl() != null) {
            posting.setCompanyLogoUrl(req.companyLogoUrl());
        }
        if (req.title() != null) {
            posting.setTitle(req.title());
        }
        if (req.employmentType() != null && !req.employmentType().isBlank()) {
            posting.setEmploymentType(parseEnum(req.employmentType(), EmploymentType.class, posting.getEmploymentType()));
        }
        if (req.jobCategory() != null && !req.jobCategory().isBlank()) {
            posting.setJobCategory(parseEnum(req.jobCategory(), JobPostingCategory.class, posting.getJobCategory()));
        }
        if (req.experienceLevel() != null && !req.experienceLevel().isBlank()) {
            posting.setExperienceLevel(parseEnum(req.experienceLevel(), PostingExperienceLevel.class, posting.getExperienceLevel()));
        }
        if (req.location() != null) {
            posting.setLocation(req.location());
        }
        if (req.salaryDisplay() != null) {
            posting.setSalaryDisplay(req.salaryDisplay());
        }
        if (Boolean.TRUE.equals(req.rollingDeadline())) {
            posting.setRollingDeadline(true);
            posting.setDeadline(null);
        } else {
            if (req.rollingDeadline() != null) {
                posting.setRollingDeadline(false);
            }
            if (req.deadline() != null && !req.deadline().isBlank()) {
                try {
                    posting.setDeadline(LocalDate.parse(req.deadline().trim()));
                    posting.setRollingDeadline(false);
                } catch (DateTimeParseException ignored) {
                    posting.setDeadline(null);
                }
            } else if (req.rollingDeadline() != null) {
                posting.setDeadline(null);
            }
        }
        if (req.applyUrl() != null && !req.applyUrl().isBlank()) {
            posting.setApplyUrl(req.applyUrl());
        } else if (posting.getApplyUrl() == null || posting.getApplyUrl().isBlank()) {
            posting.setApplyUrl(posting.getSourceUrl());
        }
        if (req.responsibilities() != null) {
            posting.setResponsibilities(cleanLines(req.responsibilities(), 20));
        }
        if (req.requirements() != null) {
            posting.setRequirementBullets(cleanLines(req.requirements(), 20));
        }
        if (req.preferredQualifications() != null) {
            posting.setPreferredQualificationBullets(cleanLines(req.preferredQualifications(), 20));
        }
        if (req.benefits() != null) {
            posting.setBenefits(cleanLines(req.benefits(), 20));
        }
        if (req.hiringProcess() != null) {
            posting.setHiringProcess(cleanLines(req.hiringProcess(), 12));
        }
        if (req.jdImageUrls() != null) {
            posting.setJdImageUrls(cleanJdImageUrls(req.jdImageUrls(), 12));
        }

        if (posting.getDeadline() != null && posting.getDeadline().isBefore(LocalDate.now())) {
            posting.setStatus(JobPostingStatus.EXPIRED);
        }
    }

    /**
     * 이미지 JD 등으로 {@link JobParseStatus#SKIPPED_IMAGE}를 유지한 채, ingest 요청의 스킬만 반영합니다.
     */
    private void applyIngestSkillListsWithoutChangingParseStatus(JobPosting posting, JobIngestRequest req) {
        List<String> reqS = req.requiredSkills() != null ? req.requiredSkills() : List.of();
        List<String> pref = req.preferredSkills() != null ? req.preferredSkills() : List.of();
        if (reqS.isEmpty() && pref.isEmpty()) {
            return;
        }
        List<String> cleanReq = reqS.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        List<String> cleanPref = pref.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        posting.setRequiredSkills(new ArrayList<>(cleanReq));
        posting.setPreferredSkills(new ArrayList<>(cleanPref));
        Set<String> seen = new LinkedHashSet<>();
        List<String> ts = new ArrayList<>();
        for (String s : cleanReq) {
            if (seen.add(s)) {
                ts.add(s);
            }
        }
        for (String s : cleanPref) {
            if (seen.add(s)) {
                ts.add(s);
            }
        }
        posting.setTechStack(ts);
    }

    private List<String> cleanJdImageUrls(List<String> raw, int limit) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String u = s.trim();
            if (u.length() > 2048) {
                continue;
            }
            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                continue;
            }
            if (!seen.add(u)) {
                continue;
            }
            out.add(u);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private List<String> cleanLines(List<String> raw, int limit) {
        if (raw == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .limit(limit)
                .toList());
    }

    private <E extends Enum<E>> E parseEnum(String raw, Class<E> type, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
