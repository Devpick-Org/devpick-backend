package com.devpick.domain.job.service;

import com.devpick.domain.job.client.JobAiClient;
import com.devpick.domain.job.repository.JobInterviewQaRepository;
import com.devpick.domain.job.repository.JobPostingRepository;
import com.devpick.domain.resume.repository.MasterResumeRepository;
import com.devpick.domain.resume.service.ResumeCryptoService;
import com.devpick.domain.subscription.service.PlanLimitService;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobInterviewServiceTest {

    @InjectMocks private JobInterviewService jobInterviewService;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private JobInterviewQaRepository jobInterviewQaRepository;
    @Mock private MasterResumeRepository masterResumeRepository;
    @Mock private ResumeCryptoService resumeCryptoService;
    @Mock private JobAiClient jobAiClient;
    @Mock private JobService jobService;
    @Mock private UserRepository userRepository;
    @Mock private PlanLimitService planLimitService;

    @Test
    @DisplayName("generateAndSave — 유저 없으면 USER_NOT_FOUND 예외")
    void generateAndSave_userNotFound_throwsException() {
        given(userRepository.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> jobInterviewService.generateAndSave(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("generateAndSave — 유저 있으면 플랜 제한 체크 후 채용공고 조회")
    void generateAndSave_withUser_callsPlanLimitThenFetchesJob() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        User user = User.builder().email("u@t.kr").nickname("u")
                .job(Job.BACKEND).level(Level.JUNIOR).build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jobPostingRepository.findById(jobId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> jobInterviewService.generateAndSave(userId, jobId))
                .isInstanceOf(DevpickException.class);

        verify(planLimitService).checkAndIncrementWeekly(userId, user.getPlanType(), "interview_qa_gen");
    }
}
