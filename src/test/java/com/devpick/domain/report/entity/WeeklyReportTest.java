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
    @DisplayName("빌더 기본값 - 카운터 0, topTags null")
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
        assertThat(report.getContentsRead()).isZero();
        assertThat(report.getQuestionsCreated()).isZero();
        assertThat(report.getScrapsCount()).isZero();
        assertThat(report.getTopTags()).isNull();
        assertThat(report.getPrevWeekComparison()).isNull();
    }

    @Test
    @DisplayName("빌더로 카운터 값을 설정할 수 있다")
    void builder_withCounters() {
        // given
        User user = buildUser();

        // when
        WeeklyReport report = WeeklyReport.builder()
                .user(user)
                .weekStart(LocalDate.of(2025, 3, 3))
                .weekEnd(LocalDate.of(2025, 3, 9))
                .contentsRead(10)
                .questionsCreated(3)
                .scrapsCount(5)
                .topTags("[\"spring\",\"jpa\",\"java\"]")
                .build();

        // then
        assertThat(report.getContentsRead()).isEqualTo(10);
        assertThat(report.getQuestionsCreated()).isEqualTo(3);
        assertThat(report.getScrapsCount()).isEqualTo(5);
        assertThat(report.getTopTags()).contains("spring");
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
