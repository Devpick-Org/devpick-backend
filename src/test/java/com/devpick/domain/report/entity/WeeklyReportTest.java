package com.devpick.domain.report.entity;

import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WeeklyReport Entity 단위 테스트")
class WeeklyReportTest {

    @Test
    @DisplayName("빌더 기본값 - status 'generated', shareToken null")
    void builder_defaultValues() {
        // given
        User user = buildUser();

        // when
        WeeklyReport report = WeeklyReport.builder()
                .user(user)
                .weekStart(LocalDate.of(2025, 3, 3))
                .weekEnd(LocalDate.of(2025, 3, 9))
                .build();

        // then
        assertThat(report.getStatus()).isEqualTo("generated");
        assertThat(report.getShareToken()).isNull();
        assertThat(report.getActivities()).isEmpty();
    }

    @Test
    @DisplayName("빌더로 shareToken 과 status 를 설정할 수 있다")
    void builder_withShareTokenAndStatus() {
        // given
        User user = buildUser();

        // when
        WeeklyReport report = WeeklyReport.builder()
                .user(user)
                .weekStart(LocalDate.of(2025, 3, 3))
                .weekEnd(LocalDate.of(2025, 3, 9))
                .shareToken("abc-token-123")
                .status("generated")
                .build();

        // then
        assertThat(report.getShareToken()).isEqualTo("abc-token-123");
        assertThat(report.getStatus()).isEqualTo("generated");
    }

    private User buildUser() {
        return User.builder()
                .email("hay@devpick.com")
                .nickname("하영")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();
    }
}
