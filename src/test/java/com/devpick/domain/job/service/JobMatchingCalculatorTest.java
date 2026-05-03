package com.devpick.domain.job.service;

import com.devpick.domain.job.entity.EmploymentType;
import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.entity.JobPostingCategory;
import com.devpick.domain.job.entity.PostingExperienceLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobMatchingCalculatorTest {

    @Test
    @DisplayName("경력 매칭: 주니어 공고에 과다 연차 후보도 충족(하한 검사)")
    void experienceScoreMet_overqualifiedJuniorStillMet() {
        assertThat(JobMatchingCalculator.experienceScoreMet(PostingExperienceLevel.JUNIOR, 15))
                .isEqualTo(1);
        assertThat(JobMatchingCalculator.experienceScoreMet(PostingExperienceLevel.JUNIOR, 3))
                .isEqualTo(1);
        assertThat(JobMatchingCalculator.experienceScoreMet(PostingExperienceLevel.NEW, 0)).isEqualTo(1);
    }

    @Test
    @DisplayName("경력 매칭: 미들 급 미만 연차 비충족")
    void experienceScoreMet_middleInsufficientYears() {
        assertThat(JobMatchingCalculator.experienceScoreMet(PostingExperienceLevel.MIDDLE, 1))
                .isEqualTo(0);
        assertThat(JobMatchingCalculator.experienceScoreMet(PostingExperienceLevel.MIDDLE, 2))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("경력 매칭: 시니어 공고 미만 연차 비충족")
    void experienceScoreMet_seniorBarrier() {
        assertThat(JobMatchingCalculator.experienceScoreMet(PostingExperienceLevel.SENIOR, 4))
                .isEqualTo(0);
        assertThat(JobMatchingCalculator.experienceScoreMet(PostingExperienceLevel.SENIOR, 5))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("우대 스킬 없을 때 가중치에서 제외(가짜 100점 방지)")
    void compute_whenPreferredSkillsEmpty_ignorePreferredWeightInScore() {
        JobPosting onlyRequired = dummyPosting(List.of("java"), List.of());
        JobMatchingCalculator.MatchResult mNoPref =
                JobMatchingCalculator.compute(onlyRequired, skills("java", 70));

        JobPosting withPrefMismatch = dummyPosting(List.of("java"), List.of("kotlin"));
        JobMatchingCalculator.MatchResult mPrefMismatch =
                JobMatchingCalculator.compute(withPrefMismatch, skills("java", 70));

        assertThat(mNoPref.requiredRatio()).isEqualTo(1.0);
        assertThat(mNoPref.preferredRatio()).isEqualTo(0.0);
        assertThat(mNoPref.matchScore()).isGreaterThanOrEqualTo(88);

        assertThat(mPrefMismatch.matchScore()).isLessThanOrEqualTo(mNoPref.matchScore());
    }

    @Test
    @DisplayName("이력서 표기(ts)와 공고 표기(TypeScript)가 동일 필수 스킬로 매칭")
    void compute_resumeAliasMatchesPostingSkill() {
        JobPosting jp = dummyPosting(List.of("TypeScript", "PostgreSQL"), List.of());
        Map<String, Integer> user = new HashMap<>();
        user.put("ts", 80);
        user.put("pgsql", 70);
        JobMatchingCalculator.MatchResult r = JobMatchingCalculator.compute(jp, user);
        assertThat(r.matchScore()).isGreaterThanOrEqualTo(85);
        assertThat(r.missingTags()).isEmpty();
    }

    private static JobPosting dummyPosting(List<String> required, List<String> preferred) {
        JobPosting jp = JobPosting.builder()
                .sourceUrl("http://test.local/" + UUID.randomUUID())
                .companyName("T")
                .title("Dev")
                .employmentType(EmploymentType.FULL_TIME)
                .jobCategory(JobPostingCategory.BACKEND)
                .experienceLevel(PostingExperienceLevel.JUNIOR)
                .location("")
                .build();
        jp.applyParsedSkills(required, preferred);
        return jp;
    }

    private static Map<String, Integer> skills(String name, int proficiency) {
        Map<String, Integer> map = new HashMap<>();
        map.put(name, proficiency);
        return map;
    }
}
