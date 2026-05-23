package com.devpick.domain.job.service;

import com.devpick.domain.job.dto.JobApiModels.JobDetailResponse;
import com.devpick.domain.job.entity.EmploymentType;
import com.devpick.domain.job.entity.JobParseStatus;
import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.entity.JobPostingCategory;
import com.devpick.domain.job.entity.JobPostingStatus;
import com.devpick.domain.job.entity.PostingExperienceLevel;
import com.devpick.domain.job.repository.JobBookmarkRepository;
import com.devpick.domain.job.repository.JobPostingRepository;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.service.PointService;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.entity.UserTag;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @InjectMocks private JobService jobService;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private JobBookmarkRepository jobBookmarkRepository;
    @Mock private com.devpick.domain.resume.repository.MasterResumeRepository masterResumeRepository;
    @Mock private com.devpick.domain.resume.service.ResumeCryptoService resumeCryptoService;
    @Mock private UserRepository userRepository;
    @Mock private com.devpick.domain.user.repository.TagRepository tagRepository;
    @Mock private com.devpick.domain.content.repository.ContentRepository contentRepository;
    @Mock private com.devpick.domain.job.client.JobAiClient jobAiClient;
    @Mock private ObjectMapper objectMapper;
    @Mock private HistoryRepository historyRepository;
    @Mock private PointService pointService;

    @Test
    @DisplayName("채용공고 북마크 시 activity 히스토리 및 포인트가 기록된다")
    void bookmark_newBookmark_recordsHistoryAndPoint() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        JobPosting job = mock(JobPosting.class);
        given(job.getTitle()).willReturn("백엔드 개발자");
        given(job.getCompanyName()).willReturn("카카오");
        given(jobPostingRepository.findById(jobId)).willReturn(Optional.of(job));
        given(jobBookmarkRepository.existsByUserIdAndJobPosting_Id(userId, jobId)).willReturn(false);

        User user = mock(User.class);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        jobService.bookmark(userId, jobId);

        then(historyRepository).should().save(argThat(h ->
                "job_bookmarked".equals(h.getActionType()) && h.getJobPosting() == job));
        then(pointService).should().earn(user, PointAction.JOB_BOOKMARK, jobId);
    }

    @Test
    @DisplayName("이미 북마크한 공고는 히스토리 및 포인트가 기록되지 않는다")
    void bookmark_alreadyBookmarked_doesNotRecordHistoryOrPoint() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        JobPosting job = mock(JobPosting.class);
        given(job.getTitle()).willReturn("백엔드 개발자");
        given(job.getCompanyName()).willReturn("카카오");
        given(jobPostingRepository.findById(jobId)).willReturn(Optional.of(job));
        given(jobBookmarkRepository.existsByUserIdAndJobPosting_Id(userId, jobId)).willReturn(true);

        jobService.bookmark(userId, jobId);

        then(historyRepository).should(never()).save(any());
        then(pointService).should(never()).earn(any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자 북마크 시 예외가 발생한다")
    void bookmark_userNotFound_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        JobPosting job = mock(JobPosting.class);
        given(job.getTitle()).willReturn("백엔드 개발자");
        given(job.getCompanyName()).willReturn("카카오");
        given(jobPostingRepository.findById(jobId)).willReturn(Optional.of(job));
        given(jobBookmarkRepository.existsByUserIdAndJobPosting_Id(userId, jobId)).willReturn(false);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.bookmark(userId, jobId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(
                        ((DevpickException) e).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    private static final UUID INTERNAL_OPS_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private JobPosting mockListableJob(UUID jobId) {
        JobPosting job = mock(JobPosting.class);
        given(job.getId()).willReturn(jobId);
        given(job.getTitle()).willReturn("백엔드 개발자");
        given(job.getCompanyName()).willReturn("카카오");
        given(job.getTechStack()).willReturn(List.of());
        given(job.getRequiredSkills()).willReturn(List.of());
        given(job.getPreferredSkills()).willReturn(List.of());
        given(job.getCompanyLogoUrl()).willReturn(null);
        given(job.getLocation()).willReturn("서울");
        given(job.getRollingDeadline()).willReturn(false);
        given(job.getDeadline()).willReturn(null);
        given(job.getEmploymentType()).willReturn(EmploymentType.FULL_TIME);
        given(job.getJobCategory()).willReturn(JobPostingCategory.BACKEND);
        given(job.getExperienceLevel()).willReturn(PostingExperienceLevel.JUNIOR);
        given(job.getStatus()).willReturn(JobPostingStatus.ACTIVE);
        given(job.getParseStatus()).willReturn(JobParseStatus.OK);
        given(job.getSalaryDisplay()).willReturn("");
        given(job.getApplyUrl()).willReturn("https://example.com");
        given(job.getResponsibilities()).willReturn(List.of());
        given(job.getRequirementBullets()).willReturn(List.of());
        given(job.getPreferredQualificationBullets()).willReturn(List.of());
        given(job.getBenefits()).willReturn(List.of());
        given(job.getHiringProcess()).willReturn(List.of());
        given(job.getJdImageUrls()).willReturn(List.of());
        return job;
    }

    @Test
    @DisplayName("getJobDetail — 이력서·태그 없는 유저는 resumeAvailable=false")
    void getJobDetail_noSkills_resumeAvailableFalse() {
        UUID jobId = UUID.randomUUID();
        JobPosting job = mockListableJob(jobId);

        given(jobPostingRepository.findById(jobId)).willReturn(Optional.of(job));
        given(masterResumeRepository.findByUserId(INTERNAL_OPS_USER_ID)).willReturn(Optional.empty());
        given(userRepository.findById(INTERNAL_OPS_USER_ID)).willReturn(Optional.empty());
        given(jobBookmarkRepository.existsByUserIdAndJobPosting_Id(INTERNAL_OPS_USER_ID, jobId)).willReturn(false);

        JobDetailResponse response = jobService.getJobDetail(INTERNAL_OPS_USER_ID, jobId);

        assertThat(response.resumeAvailable()).isFalse();
    }

    @Test
    @DisplayName("getJobDetail — 기술 태그가 등록된 유저는 resumeAvailable=true")
    void getJobDetail_hasTagSkills_resumeAvailableTrue() {
        UUID jobId = UUID.randomUUID();
        JobPosting job = mockListableJob(jobId);

        Tag tag = mock(Tag.class);
        given(tag.getName()).willReturn("Java");
        UserTag userTag = mock(UserTag.class);
        given(userTag.getTag()).willReturn(tag);
        User userWithTags = mock(User.class);
        given(userWithTags.getUserTags()).willReturn(List.of(userTag));

        given(jobPostingRepository.findById(jobId)).willReturn(Optional.of(job));
        given(masterResumeRepository.findByUserId(INTERNAL_OPS_USER_ID)).willReturn(Optional.empty());
        given(userRepository.findById(INTERNAL_OPS_USER_ID)).willReturn(Optional.of(userWithTags));
        given(jobBookmarkRepository.existsByUserIdAndJobPosting_Id(INTERNAL_OPS_USER_ID, jobId)).willReturn(false);

        JobDetailResponse response = jobService.getJobDetail(INTERNAL_OPS_USER_ID, jobId);

        assertThat(response.resumeAvailable()).isTrue();
    }

    @Test
    @DisplayName("getJobDetail — 경력 ANY 공고는 breakdown 경력 섹션이 점수 게이지 없이 무관 표시")
    void getJobDetail_experienceAny_showsNonScoredExperienceSection() {
        UUID jobId = UUID.randomUUID();
        JobPosting job = mockListableJob(jobId);
        given(job.getExperienceLevel()).willReturn(PostingExperienceLevel.ANY);
        given(job.getRequiredSkills()).willReturn(List.of("Java"));
        given(job.getPreferredSkills()).willReturn(List.of("MS-Office"));

        Tag tag = mock(Tag.class);
        given(tag.getName()).willReturn("Java");
        UserTag userTag = mock(UserTag.class);
        given(userTag.getTag()).willReturn(tag);
        User userWithTags = mock(User.class);
        given(userWithTags.getUserTags()).willReturn(List.of(userTag));

        given(jobPostingRepository.findById(jobId)).willReturn(Optional.of(job));
        given(masterResumeRepository.findByUserId(INTERNAL_OPS_USER_ID)).willReturn(Optional.empty());
        given(userRepository.findById(INTERNAL_OPS_USER_ID)).willReturn(Optional.of(userWithTags));
        given(jobBookmarkRepository.existsByUserIdAndJobPosting_Id(INTERNAL_OPS_USER_ID, jobId))
                .willReturn(false);

        JobDetailResponse response = jobService.getJobDetail(INTERNAL_OPS_USER_ID, jobId);

        assertThat(response.matchBreakdown().experience().maxScore()).isZero();
        assertThat(response.matchBreakdown().experience().score()).isZero();
        assertThat(response.matchBreakdown().experience().summary()).contains("경력 무관");
        assertThat(response.matchBreakdown().experience().items().get(0).label()).isEqualTo("경력 제한 없음");
    }
}
