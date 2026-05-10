package com.devpick.domain.job.service;

import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.job.repository.JobBookmarkRepository;
import com.devpick.domain.job.repository.JobPostingRepository;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.point.service.PointService;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.User;
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

import java.util.Optional;
import java.util.UUID;

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
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(
                        ((DevpickException) e).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
