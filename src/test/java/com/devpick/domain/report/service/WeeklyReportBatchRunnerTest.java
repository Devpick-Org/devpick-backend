package com.devpick.domain.report.service;

import com.devpick.domain.report.repository.WeeklyReportRepository;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WeeklyReportBatchRunnerTest {

    @InjectMocks
    private WeeklyReportBatchRunner batchRunner;

    @Mock
    private WeeklyReportService weeklyReportService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WeeklyReportRepository weeklyReportRepository;

    private UUID userId;
    private User activeUser;
    private LocalDate weekStart;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        weekStart = LocalDate.now().minusWeeks(1);

        activeUser = User.builder()
                .email("test@devpick.kr")
                .nickname("tester")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(activeUser, "id", userId);
    }

    @Test
    @DisplayName("createReportIfAbsent — 활성 유저 + 리포트 없으면 생성 후 1 반환")
    void createReportIfAbsent_newActiveUser_returns1() {
        given(userRepository.findById(userId)).willReturn(Optional.of(activeUser));
        given(weeklyReportRepository.existsByUser_IdAndWeekStart(userId, weekStart)).willReturn(false);

        int result = batchRunner.createReportIfAbsent(userId, weekStart);

        assertThat(result).isEqualTo(1);
        verify(weeklyReportService).createWeeklyReportForUser(eq(activeUser), eq(weekStart), any());
    }

    @Test
    @DisplayName("createReportIfAbsent — 이미 존재하는 리포트면 0 반환")
    void createReportIfAbsent_alreadyExists_returns0() {
        given(userRepository.findById(userId)).willReturn(Optional.of(activeUser));
        given(weeklyReportRepository.existsByUser_IdAndWeekStart(userId, weekStart)).willReturn(true);

        int result = batchRunner.createReportIfAbsent(userId, weekStart);

        assertThat(result).isEqualTo(0);
        verify(weeklyReportService, never()).createWeeklyReportForUser(any(), any(), any());
    }

    @Test
    @DisplayName("createReportIfAbsent — 비활성 유저(isActive=false)면 0 반환")
    void createReportIfAbsent_inactiveUser_returns0() {
        ReflectionTestUtils.setField(activeUser, "isActive", false);
        given(userRepository.findById(userId)).willReturn(Optional.of(activeUser));

        int result = batchRunner.createReportIfAbsent(userId, weekStart);

        assertThat(result).isEqualTo(0);
        verify(weeklyReportService, never()).createWeeklyReportForUser(any(), any(), any());
    }

    @Test
    @DisplayName("createReportIfAbsent — 탈퇴 유저(deletedAt 있음)면 0 반환")
    void createReportIfAbsent_deletedUser_returns0() {
        ReflectionTestUtils.setField(activeUser, "deletedAt", java.time.LocalDateTime.now());
        given(userRepository.findById(userId)).willReturn(Optional.of(activeUser));

        int result = batchRunner.createReportIfAbsent(userId, weekStart);

        assertThat(result).isEqualTo(0);
        verify(weeklyReportService, never()).createWeeklyReportForUser(any(), any(), any());
    }
}
