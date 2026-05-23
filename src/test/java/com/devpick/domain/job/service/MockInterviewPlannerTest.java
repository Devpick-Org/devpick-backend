package com.devpick.domain.job.service;

import com.devpick.domain.job.dto.MockInterviewModels.QuestionPlanResponse;
import com.devpick.domain.job.entity.JobPostingCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockInterviewPlannerTest {

    private final MockInterviewPlanner planner = new MockInterviewPlanner();

    @Test
    @DisplayName("플랜은 항상 15문항 2-4-4-4-1 페이즈 구조를 만든다")
    void plan_alwaysFifteenWithFiveStagePhases() {
        QuestionPlanResponse plan = planner.plan(
                JobPostingCategory.FRONTEND,
                "Frontend Engineer",
                "TestCo",
                List.of("React", "TypeScript"),
                List.of("Next.js"),
                List.of("React", "TypeScript")
        );

        assertThat(plan.questions()).hasSize(15);
        assertThat(countByPhase(plan, "WARM_UP")).isEqualTo(2);
        assertThat(countByPhase(plan, "PROJECT")).isEqualTo(4);
        assertThat(countByPhase(plan, "DOMAIN")).isEqualTo(4);
        assertThat(countByPhase(plan, "CS_INFRA")).isEqualTo(4);
        assertThat(countByPhase(plan, "BEHAVIORAL")).isEqualTo(1);
    }

    @Test
    @DisplayName("JD에는 있지만 이력서에 없는 키워드가 있으면 CS_INFRA에 강제 1문항이 들어간다")
    void plan_forcesJdGapKeyword() {
        // JD: Sentry/Monitoring 토픽이 들어가도록 키워드를 넣고, 이력서에는 없게 함
        QuestionPlanResponse plan = planner.plan(
                JobPostingCategory.FRONTEND,
                "FE",
                "TestCo",
                List.of("Sentry"),
                List.of(),
                List.of("React", "TypeScript")
        );

        assertThat(plan.jdGapKeywords()).contains("sentry");
        long jdOnlyCount = plan.questions().stream()
                .filter(q -> q.phase().equals("CS_INFRA") && q.jdOnlyKeyword())
                .count();
        assertThat(jdOnlyCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("이력서·회사 정보가 있으면 자기소개 문항에 구체 명사가 포함된다")
    void plan_personalizesWarmUpWithResumeAndCompany() {
        MockInterviewResumeContext resume = new MockInterviewResumeContext(
                "Backend Engineer",
                "5년차",
                "API와 캐시 설계",
                List.of("Java", "Kafka"),
                "DevPick Labs",
                "Backend Engineer",
                "Acme Payments",
                "Fleet Dispatch Simulator",
                "Study Log API",
                "Redis",
                "p95 지연 개선"
        );
        MockInterviewJdContext jd = new MockInterviewJdContext(
                List.of("커머스 API 고도화", "캐시 전략 수립"),
                "커머스 API · 캐시"
        );
        QuestionPlanResponse plan = planner.plan(
                JobPostingCategory.BACKEND,
                "Backend Engineer",
                "Marshmallow",
                List.of("Java", "Redis"),
                List.of("Kafka"),
                resume,
                jd
        );

        String intro = plan.questions().get(0).prompt();
        String motivation = plan.questions().get(1).prompt();
        assertThat(intro + motivation)
                .containsAnyOf("DevPick Labs", "Fleet Dispatch Simulator", "Marshmallow", "커머스", "API와 캐시");
        assertThat(motivation).contains("Marshmallow");
        assertThat(intro).doesNotContain("핵심 경험 한두 가지를 1분 내로");
    }

    @Test
    @DisplayName("백엔드 CS_INFRA 첫 문항은 브라우저 렌더링이 아니다")
    void plan_backendCoreCsIsNotBrowserRendering() {
        MockInterviewResumeContext resume = MockInterviewResumeContext.empty();
        QuestionPlanResponse plan = planner.plan(
                JobPostingCategory.BACKEND,
                "Server Engineer",
                "TestCo",
                List.of("Spring Boot"),
                List.of(),
                resume,
                MockInterviewJdContext.empty()
        );
        String corePrompt = plan.questions().stream()
                .filter(q -> q.phase().equals("CS_INFRA"))
                .findFirst()
                .orElseThrow()
                .prompt();
        assertThat(corePrompt).doesNotContain("브라우저 렌더링");
        assertThat(corePrompt).containsAnyOf("API", "트랜잭션", "동시성");
    }

    @Test
    @DisplayName("백엔드 카테고리에서는 직무 페이즈 토픽이 백엔드 라벨로 잡힌다")
    void plan_backendDomainTopicsLabel() {
        MockInterviewResumeContext resume = MockInterviewResumeContext.empty();
        QuestionPlanResponse plan = planner.plan(
                JobPostingCategory.BACKEND,
                "Server Engineer",
                "TestCo",
                List.of("Spring Boot", "PostgreSQL"),
                List.of("Kafka"),
                resume,
                MockInterviewJdContext.empty()
        );
        assertThat(plan.domainLabel()).isEqualTo("BACKEND");
        assertThat(countByPhase(plan, "DOMAIN")).isEqualTo(4);
    }

    private long countByPhase(QuestionPlanResponse plan, String phase) {
        return plan.questions().stream().filter(q -> q.phase().equals(phase)).count();
    }
}
