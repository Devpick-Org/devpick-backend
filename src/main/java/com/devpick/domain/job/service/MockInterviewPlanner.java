package com.devpick.domain.job.service;

import com.devpick.domain.job.dto.MockInterviewModels.QuestionPlanItem;
import com.devpick.domain.job.dto.MockInterviewModels.QuestionPlanResponse;
import com.devpick.domain.job.entity.JobPostingCategory;
import com.devpick.domain.job.entity.MockInterviewPhase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2-4-4-4-1 페이즈 구조에 맞춰 15문항 면접 플랜을 만들고, JD 갭 키워드를 강제 포함한다.
 *
 * <p>AI 호출이 실패하거나 빈 응답을 줄 때 사용하는 서버 사이드 백업도 책임진다.</p>
 */
@Component
public class MockInterviewPlanner {

    public static final int TOTAL_QUESTIONS = 15;

    private static final List<String> CORE_CS_TOPICS = List.of(
            "Browser Internals & Rendering Pipeline",
            "HTTP, Network, Caching"
    );

    private static final List<String> EXTENDED_CS_TOPICS = List.of(
            "Web Security",
            "Core Web Vitals",
            "Accessibility",
            "Monorepo",
            "Micro Frontends",
            "Monitoring",
            "Testing",
            "FSD / Architecture",
            "CI/CD",
            "Authentication",
            "State Management",
            "i18n",
            "WebSocket / SSE",
            "PWA",
            "SEO",
            "Design System",
            "Error Handling",
            "Image Optimization",
            "AI-assisted Dev"
    );

    private static final Map<JobPostingCategory, String> DOMAIN_LABEL = Map.of(
            JobPostingCategory.FRONTEND, "FRONTEND",
            JobPostingCategory.BACKEND, "BACKEND",
            JobPostingCategory.FULLSTACK, "FULLSTACK",
            JobPostingCategory.DEVOPS, "DEVOPS",
            JobPostingCategory.AI_ML, "AI/ML",
            JobPostingCategory.MOBILE, "MOBILE",
            JobPostingCategory.DATA, "DATA"
    );

    public QuestionPlanResponse plan(
            JobPostingCategory category,
            String jobTitle,
            String companyName,
            List<String> requiredSkills,
            List<String> preferredSkills,
            List<String> resumeSkills
    ) {
        String domainLabel = DOMAIN_LABEL.getOrDefault(category, "FRONTEND");
        Set<String> jdSkills = canonicalSet(requiredSkills);
        jdSkills.addAll(canonicalSet(preferredSkills));
        Set<String> resumeSet = canonicalSet(resumeSkills);
        List<String> jdGap = jdSkills.stream()
                .filter(s -> !resumeSet.contains(s))
                .toList();

        List<QuestionPlanItem> items = new ArrayList<>();
        int qNo = 1;

        items.add(item(qNo++, MockInterviewPhase.WARM_UP, "자기소개",
                "지원 직무 관점에서 본인의 강점과 핵심 경험 한두 가지를 1분 내로 소개해 주세요.", false, List.of()));
        items.add(item(qNo++, MockInterviewPhase.WARM_UP, "이직동기",
                companyName != null && !companyName.isBlank()
                        ? companyName + "과 이 포지션에 지원하신 동기와 기대하는 성장 방향을 설명해 주세요."
                        : "이번 이직(혹은 지원)을 통해 이루고 싶은 성장 방향과 기대하는 환경을 설명해 주세요.",
                false, List.of()));

        items.add(item(qNo++, MockInterviewPhase.PROJECT, "기술 결정",
                "최근 프로젝트에서 핵심 기술 스택을 선택한 이유와, 다른 후보를 배제한 근거를 설명해 주세요.",
                false, List.of()));
        items.add(item(qNo++, MockInterviewPhase.PROJECT, "트레이드오프",
                "프로젝트 도중 마주친 가장 큰 트레이드오프 1가지를 들고, 어떤 기준으로 결정했는지 말씀해 주세요.",
                false, List.of()));
        items.add(item(qNo++, MockInterviewPhase.PROJECT, "문제 해결",
                "재현이 까다로웠던 버그/장애를 어떻게 진단하고 해결했는지, 사용한 도구·로그·가설 검증 절차를 설명해 주세요.",
                false, List.of()));
        items.add(item(qNo++, MockInterviewPhase.PROJECT, "성과/임팩트",
                "본인이 주도한 변경 중 측정 가능한 임팩트가 있었던 사례 1가지를 수치 중심으로 공유해 주세요.",
                false, List.of()));

        List<String> domainTopics = domainQuestions(category, jobTitle);
        for (int i = 0; i < 4; i++) {
            String topic = domainTopics.get(Math.min(i, domainTopics.size() - 1));
            items.add(item(qNo++, MockInterviewPhase.DOMAIN, topic, topic + " 와 관련해 본인이 가진 가장 깊은 경험을 구체적인 예와 함께 설명해 주세요.",
                    false, List.of()));
        }

        List<String> coreTopic = List.of(CORE_CS_TOPICS.get(0));
        items.add(item(qNo++, MockInterviewPhase.CS_INFRA, coreTopic.get(0),
                "브라우저 렌더링 파이프라인(파싱→스타일→레이아웃→페인트→컴포지트)에서 어떤 단계가 가장 비싸게 동작했는지, 실제로 본인이 최적화한 사례를 설명해 주세요.",
                false, List.of("renderingPipeline", "reflow")));

        List<String> extendedChosen = chooseExtendedTopics(jdSkills, resumeSet, jdGap);
        for (String topic : extendedChosen) {
            boolean jdOnly = isJdGapTopic(topic, jdGap);
            items.add(item(qNo++, MockInterviewPhase.CS_INFRA, topic,
                    topic + " 관련 본인 경험과, 면접관이 검증할 만한 기술적 디테일을 한두 가지 짚어가며 설명해 주세요.",
                    jdOnly, related(topic)));
        }

        items.add(item(qNo, MockInterviewPhase.BEHAVIORAL, "협업·DX",
                "협업 도중 의견이 갈렸을 때 합의에 이르렀거나 DX(개발자 경험)를 개선한 사례 1가지를 STAR 구조로 공유해 주세요.",
                false, List.of()));

        return new QuestionPlanResponse(items, coreTopic, extendedChosen, jdGap, domainLabel);
    }

    private List<String> chooseExtendedTopics(Set<String> jdSkills, Set<String> resumeSet, List<String> jdGap) {
        List<String> jdOnly = new ArrayList<>();
        for (String topic : EXTENDED_CS_TOPICS) {
            if (relatedKeywords(topic).stream().anyMatch(k -> jdGap.contains(k.toLowerCase()))) {
                jdOnly.add(topic);
            }
        }

        List<String> matched = new ArrayList<>();
        for (String topic : EXTENDED_CS_TOPICS) {
            if (jdOnly.contains(topic)) {
                continue;
            }
            if (relatedKeywords(topic).stream().anyMatch(k -> jdSkills.contains(k.toLowerCase()) || resumeSet.contains(k.toLowerCase()))) {
                matched.add(topic);
            }
        }

        List<String> rest = new ArrayList<>(EXTENDED_CS_TOPICS);
        rest.removeAll(jdOnly);
        rest.removeAll(matched);

        Set<String> picked = new LinkedHashSet<>();
        if (!jdOnly.isEmpty()) {
            picked.add(jdOnly.get(0));
        }
        for (String t : matched) {
            if (picked.size() >= 3) break;
            picked.add(t);
        }
        for (String t : rest) {
            if (picked.size() >= 3) break;
            picked.add(t);
        }
        for (String t : EXTENDED_CS_TOPICS) {
            if (picked.size() >= 3) break;
            picked.add(t);
        }
        return new ArrayList<>(picked).subList(0, 3);
    }

    private boolean isJdGapTopic(String topic, List<String> jdGap) {
        return relatedKeywords(topic).stream().anyMatch(k -> jdGap.contains(k.toLowerCase()));
    }

    private List<String> related(String topic) {
        return new ArrayList<>(relatedKeywords(topic));
    }

    private List<String> relatedKeywords(String topic) {
        return switch (topic) {
            case "Web Security" -> List.of("xss", "csrf", "owasp", "cors");
            case "Core Web Vitals" -> List.of("lcp", "inp", "cls", "performance");
            case "Accessibility" -> List.of("a11y", "wcag", "aria");
            case "Monorepo" -> List.of("turborepo", "nx", "pnpm", "monorepo");
            case "Micro Frontends" -> List.of("mfe", "module-federation", "micro frontend");
            case "Monitoring" -> List.of("sentry", "datadog", "observability");
            case "Testing" -> List.of("jest", "playwright", "cypress", "vitest", "junit");
            case "FSD / Architecture" -> List.of("fsd", "architecture", "ddd");
            case "CI/CD" -> List.of("github actions", "circleci", "jenkins", "argocd");
            case "Authentication" -> List.of("oauth", "jwt", "openid", "saml");
            case "State Management" -> List.of("zustand", "redux", "react query", "tanstack");
            case "i18n" -> List.of("i18n", "intl", "next-intl");
            case "WebSocket / SSE" -> List.of("websocket", "sse", "stomp");
            case "PWA" -> List.of("pwa", "service worker", "manifest");
            case "SEO" -> List.of("seo", "ssr", "metadata");
            case "Design System" -> List.of("storybook", "design system", "tokens");
            case "Error Handling" -> List.of("error boundary", "sentry", "tracing");
            case "Image Optimization" -> List.of("avif", "webp", "next/image");
            case "AI-assisted Dev" -> List.of("copilot", "cursor", "claude", "llm");
            default -> List.of();
        };
    }

    private List<String> domainQuestions(JobPostingCategory category, String jobTitle) {
        if (category == null) {
            return defaultFrontend();
        }
        return switch (category) {
            case FRONTEND -> defaultFrontend();
            case BACKEND -> List.of(
                    "프레임워크/런타임 동작",
                    "트랜잭션과 동시성 제어",
                    "캐시·DB 인덱싱 전략",
                    "관측성·로그 설계");
            case FULLSTACK -> List.of(
                    "프론트-백엔드 인터페이스 설계",
                    "API 계약과 버저닝",
                    "성능과 사용자 경험",
                    "보안과 인증");
            case DEVOPS -> List.of(
                    "배포 파이프라인 설계",
                    "관측성과 알림",
                    "인프라 코드(IaC) 사용",
                    "장애 대응 시나리오");
            case AI_ML -> List.of(
                    "데이터 파이프라인",
                    "모델 평가 지표",
                    "프롬프트/추론 비용 관리",
                    "학습/배포 운영");
            case MOBILE -> List.of(
                    "네이티브/하이브리드 트레이드오프",
                    "성능과 메모리",
                    "오프라인/네트워크 처리",
                    "릴리즈 파이프라인");
            case DATA -> List.of(
                    "데이터 모델링",
                    "ETL/ELT 설계",
                    "성능과 비용 최적화",
                    "품질 모니터링");
        };
    }

    private List<String> defaultFrontend() {
        return List.of(
                "프레임워크 내부 동작",
                "렌더링 최적화",
                "상태 관리 심화",
                "타입 안전성과 빌드 도구");
    }

    private QuestionPlanItem item(
            int no,
            MockInterviewPhase phase,
            String topic,
            String prompt,
            boolean jdOnly,
            List<String> keywords
    ) {
        return new QuestionPlanItem(no, phase.name(), topic, prompt, jdOnly, keywords);
    }

    private Set<String> canonicalSet(List<String> values) {
        Set<String> result = new HashSet<>();
        if (values == null) return result;
        for (String v : values) {
            String canon = JobSkillNormalizer.canonicalLower(v);
            if (!canon.isEmpty()) {
                result.add(canon);
            }
        }
        return result;
    }

    public List<String> coreCsTopics() {
        return new ArrayList<>(CORE_CS_TOPICS);
    }

    public List<String> extendedCsTopics() {
        return new ArrayList<>(EXTENDED_CS_TOPICS);
    }

    public List<String> defaultDomainTopics(JobPostingCategory category) {
        return new ArrayList<>(domainQuestions(category, ""));
    }

    @SuppressWarnings("unused")
    private static List<String> splitWords(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("[\\s,/]+")).filter(s -> !s.isBlank()).toList();
    }
}
