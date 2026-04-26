package com.devpick.domain.job.entity;

import com.devpick.global.entity.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "job_postings", indexes = {
        @Index(name = "idx_job_postings_status_deadline", columnList = "status, deadline"),
        @Index(name = "idx_job_postings_company", columnList = "company_name")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor
@Builder
public class JobPosting extends BaseTimeEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    @Builder.Default
    private JobSource source = JobSource.RALLIT;

    @Column(name = "source_url", nullable = false, unique = true, length = 2048)
    private String sourceUrl;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "company_logo_url", length = 1024)
    private String companyLogoUrl;

    @Column(nullable = false, length = 512)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 32)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_category", nullable = false, length = 32)
    private JobPostingCategory jobCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false, length = 32)
    private PostingExperienceLevel experienceLevel;

    @Column(length = 255)
    private String location;

    /** 화면 표시용 연봉 문자열 (예: 협의, 5,000~7,000만원) */
    @Column(name = "salary_display", length = 255)
    private String salaryDisplay;

    private LocalDate deadline;

    /**
     * 랠릿 등에서 명시적 '상시/채용 시 마감' 공고. {@link #deadline} 은 비우고 이 플래그로 UI 문구를 구분한다.
     */
    @Column(name = "rolling_deadline", nullable = false)
    @Builder.Default
    private Boolean rollingDeadline = false;

    @Column(name = "apply_url", length = 2048)
    private String applyUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private JobPostingStatus status = JobPostingStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 32)
    @Builder.Default
    private JobParseStatus parseStatus = JobParseStatus.PENDING;

    /** JD에서 추출한 불릿(선택). 본문 원문은 저장하지 않음. */
    @ElementCollection
    @CollectionTable(name = "job_posting_responsibilities", joinColumns = @JoinColumn(name = "job_posting_id"))
    @Column(name = "line", length = 2000)
    @Builder.Default
    private List<String> responsibilities = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "job_posting_requirement_bullets", joinColumns = @JoinColumn(name = "job_posting_id"))
    @Column(name = "line", length = 2000)
    @Builder.Default
    private List<String> requirementBullets = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "job_posting_preferred_bullets", joinColumns = @JoinColumn(name = "job_posting_id"))
    @Column(name = "line", length = 2000)
    @Builder.Default
    private List<String> preferredQualificationBullets = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "job_posting_benefits", joinColumns = @JoinColumn(name = "job_posting_id"))
    @Column(name = "line", length = 500)
    @Builder.Default
    private List<String> benefits = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "job_posting_hiring_process", joinColumns = @JoinColumn(name = "job_posting_id"))
    @Column(name = "line", length = 500)
    @Builder.Default
    private List<String> hiringProcess = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "job_posting_required_skills", joinColumns = @JoinColumn(name = "job_posting_id"))
    @Column(name = "skill", length = 120)
    @Builder.Default
    private List<String> requiredSkills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "job_posting_preferred_skills", joinColumns = @JoinColumn(name = "job_posting_id"))
    @Column(name = "skill", length = 120)
    @Builder.Default
    private List<String> preferredSkills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "job_posting_tech_stack", joinColumns = @JoinColumn(name = "job_posting_id"))
    @Column(name = "tech", length = 120)
    @Builder.Default
    private List<String> techStack = new ArrayList<>();

    /**
     * 원문이 이미지(인포그래픽) 위주일 때, 수집 단계에서 확보한 공고 이미지 URL.
     * 텍스트 JD가 비어 있어도 상세 화면에서 사용자에게 표시한다.
     */
    @ElementCollection
    @CollectionTable(name = "job_posting_jd_images", joinColumns = @JoinColumn(name = "job_posting_id"))
    @Column(name = "url", length = 2048)
    @Builder.Default
    private List<String> jdImageUrls = new ArrayList<>();

    public void markExpired() {
        this.status = JobPostingStatus.EXPIRED;
    }

    public void applyParsedSkills(List<String> required, List<String> preferred) {
        this.requiredSkills = required == null ? new ArrayList<>() : new ArrayList<>(required);
        this.preferredSkills = preferred == null ? new ArrayList<>() : new ArrayList<>(preferred);
        this.parseStatus = JobParseStatus.OK;
        List<String> ts = new ArrayList<>(this.requiredSkills);
        ts.addAll(this.preferredSkills);
        this.techStack = new ArrayList<>(ts.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList());
    }
}
