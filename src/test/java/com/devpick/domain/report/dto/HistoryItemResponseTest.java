package com.devpick.domain.report.dto;

import com.devpick.domain.job.entity.JobPosting;
import com.devpick.domain.point.entity.PointAction;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class HistoryItemResponseTest {

    @Test
    @DisplayName("job_bookmarked 액션은 points 5를 반환하고 jobPosting 정보를 포함한다")
    void of_jobBookmarked_returnsPointsAndJobPostingInfo() {
        UUID jobId = UUID.randomUUID();
        JobPosting jobPosting = mock(JobPosting.class);
        given(jobPosting.getId()).willReturn(jobId);
        given(jobPosting.getTitle()).willReturn("백엔드 개발자");
        given(jobPosting.getCompanyName()).willReturn("카카오");

        History history = History.builder()
                .user(mock(User.class))
                .actionType("job_bookmarked")
                .jobPosting(jobPosting)
                .build();

        HistoryItemResponse result = HistoryItemResponse.of(history, Map.of());

        assertThat(result.actionType()).isEqualTo("job_bookmarked");
        assertThat(result.points()).isEqualTo(PointAction.JOB_BOOKMARK.getPoints());
        assertThat(result.jobPosting()).isNotNull();
        assertThat(result.jobPosting().id()).isEqualTo(jobId);
        assertThat(result.jobPosting().title()).isEqualTo("백엔드 개발자");
        assertThat(result.jobPosting().companyName()).isEqualTo("카카오");
    }

    @Test
    @DisplayName("mock_interview_completed 액션은 points 20을 반환한다")
    void of_mockInterviewCompleted_returnsPoints20() {
        History history = History.builder()
                .user(mock(User.class))
                .actionType("mock_interview_completed")
                .build();

        HistoryItemResponse result = HistoryItemResponse.of(history, Map.of());

        assertThat(result.actionType()).isEqualTo("mock_interview_completed");
        assertThat(result.points()).isEqualTo(PointAction.MOCK_INTERVIEW_COMPLETE.getPoints());
        assertThat(result.jobPosting()).isNull();
    }
}
