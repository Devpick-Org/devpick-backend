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
            MockInterviewResumeContext resume,
            MockInterviewJdContext jd
    ) {
        MockInterviewResumeContext r = resume != null ? resume : MockInterviewResumeContext.empty();
        MockInterviewJdContext j = jd != null ? jd : MockInterviewJdContext.empty();
        String domainLabel = DOMAIN_LABEL.getOrDefault(category, "FRONTEND");
        Set<String> jdSkills = canonicalSet(requiredSkills);
        jdSkills.addAll(canonicalSet(preferredSkills));
        Set<String> resumeSet = canonicalSet(r.skills());
        List<String> jdGap = jdSkills.stream()
                .filter(s -> !resumeSet.contains(s))
                .toList();

        int seed = variantSeed(jobTitle, companyName, r.latestProjectName(), r.latestCompany());
        List<QuestionPlanItem> items = new ArrayList<>();
        int qNo = 1;

        items.add(item(qNo++, MockInterviewPhase.WARM_UP, "자기소개",
                warmUpIntroPrompt(r, jobTitle, companyName, j, seed), false, List.of()));
        items.add(item(qNo++, MockInterviewPhase.WARM_UP, "지원동기",
                motivationPrompt(r, jobTitle, companyName, j, seed + 1), false, List.of()));

        List<String> projectPrompts = projectPrompts(r, jdGap, seed);
        String[] projectTopics = {"기술 결정", "트레이드오프", "문제 해결", "성과/임팩트"};
        for (int i = 0; i < 4; i++) {
            items.add(item(qNo++, MockInterviewPhase.PROJECT, projectTopics[i],
                    projectPrompts.get(i), false, List.of()));
        }

        List<String> domainTopics = domainQuestions(category, jobTitle);
        List<String> skillAnchors = domainSkillAnchors(r.skills(), jdSkills, jdGap);
        for (int i = 0; i < 4; i++) {
            String topic = domainTopics.get(Math.min(i, domainTopics.size() - 1));
            String skill = skillAnchors.isEmpty() ? "" : skillAnchors.get(i % skillAnchors.size());
            items.add(item(qNo++, MockInterviewPhase.DOMAIN, topic,
                    domainPrompt(topic, skill, r, companyName, seed + i), false, List.of()));
        }

        CoreCsSlot core = coreCsForCategory(category, r, j);
        items.add(item(qNo++, MockInterviewPhase.CS_INFRA, core.topic(),
                core.prompt(), false, core.keywords()));

        List<String> extendedChosen = chooseExtendedTopics(jdSkills, resumeSet, jdGap);
        for (int i = 0; i < extendedChosen.size(); i++) {
            String topic = extendedChosen.get(i);
            boolean jdOnly = isJdGapTopic(topic, jdGap);
            items.add(item(qNo++, MockInterviewPhase.CS_INFRA, topic,
                    csInfraPrompt(topic, r, jdGap, jdOnly, seed + 10 + i), jdOnly, related(topic)));
        }

        items.add(item(qNo, MockInterviewPhase.BEHAVIORAL, "협업·성과",
                behavioralPrompt(r, companyName, seed + 20), false, List.of()));

        return new QuestionPlanResponse(items, List.of(core.topic()), extendedChosen, jdGap, domainLabel);
    }

    /** 테스트 호환 — skills만 있는 구 plan 시그니처 */
    @Deprecated
    public QuestionPlanResponse plan(
            JobPostingCategory category,
            String jobTitle,
            String companyName,
            List<String> requiredSkills,
            List<String> preferredSkills,
            List<String> resumeSkills
    ) {
        MockInterviewResumeContext resume = new MockInterviewResumeContext(
                "", "", "", resumeSkills != null ? resumeSkills : List.of(),
                "", "", "", "", "", "", ""
        );
        return plan(category, jobTitle, companyName, requiredSkills, preferredSkills,
                resume, MockInterviewJdContext.empty());
    }

    private record CoreCsSlot(String topic, String prompt, List<String> keywords) {}

    private int variantSeed(String... parts) {
        int h = 17;
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                h = 31 * h + p.hashCode();
            }
        }
        return Math.abs(h);
    }

    private String warmUpIntroPrompt(
            MockInterviewResumeContext r,
            String jobTitle,
            String companyName,
            MockInterviewJdContext jd,
            int seed
    ) {
        String role = !r.jobLabel().isBlank() ? r.jobLabel()
                : (jobTitle != null && !jobTitle.isBlank() ? jobTitle : "지원 직무");
        String exp = !r.experienceLabel().isBlank() ? r.experienceLabel() + " " : "";
        String company = blankTo(companyName, "");
        String jdFocus = jd.primaryFocus();

        String[] variants = {
                exp + role + "로서 "
                        + named(r.latestCompany(), "최근 재직 회사")
                        + "에서 맡았던 역할과, "
                        + (company.isBlank() ? "이번 포지션" : "「" + company + "」 " + blankTo(jobTitle, "포지션"))
                        + "에서 바로 써먹을 수 있는 경험을 중심으로 자기소개해 주세요.",
                !r.summarySnippet().isBlank()
                        ? "이력서 요약에 적으신 「" + truncate(r.summarySnippet(), 48) + "」을 출발점으로, "
                        + "본인만의 강점이 드러나게 1~2분 자기소개를 해 주세요."
                        : exp + role + "로서 기술적으로 가장 자신 있는 영역과 그 근거가 되는 경험을 소개해 주세요.",
                !r.latestProjectName().isBlank()
                        ? "「" + r.latestProjectName() + "」에서 "
                        + blankTo(r.latestProjectTech(), "핵심 기술")
                        + "을 다룬 경험을 중심으로, "
                        + (company.isBlank() ? "지원 직무" : company + " " + blankTo(jobTitle, "직무"))
                        + "에 왜 적합한지 설명해 주세요."
                        : exp + role + " 관점에서 최근 1년간 가장 성장했다고 느낀 역량과 사례를 소개해 주세요.",
                !jdFocus.isBlank()
                        ? "공고에 적힌 「" + truncate(jdFocus, 40) + "」 업무와 연결되는 본인 경험을 골라 자기소개해 주세요."
                        : exp + role + "로서 " + named(r.latestCompany(), "현·전직장") + "에서의 핵심 성과를 짧게 소개해 주세요."
        };
        return variants[seed % variants.length];
    }

    private String motivationPrompt(
            MockInterviewResumeContext r,
            String jobTitle,
            String companyName,
            MockInterviewJdContext jd,
            int seed
    ) {
        String company = blankTo(companyName, "");
        String title = blankTo(jobTitle, "이 포지션");
        String jdFocus = jd.primaryFocus();

        if (!company.isBlank() && !jdFocus.isBlank()) {
            String[] withJd = {
                    "「" + company + "」의 「" + truncate(jdFocus, 36) + "」 업무에 관심을 갖게 된 계기와, "
                            + named(r.latestCompany(), "이전 경험") + "에서의 어떤 경험이 연결되는지 설명해 주세요.",
                    company + " " + title + "에 지원하신 이유를, "
                            + "회사/팀이 기대하는 역량과 본인 " + blankTo(r.latestRole(), "경력") + "의 접점으로 설명해 주세요.",
                    !r.priorCompany().isBlank()
                            ? r.priorCompany() + " → " + r.latestCompany() + "을 거치며 "
                            + company + "을(를) 선택하신 이유와 기대하는 성장을 말씀해 주세요."
                            : company + "에서 " + title + "로 일하고 싶은 이유를 구체적으로 설명해 주세요."
            };
            return withJd[seed % withJd.length];
        }
        if (!company.isBlank()) {
            return company + " " + title + "에 지원하신 동기와, "
                    + "입사 후 6개월 안에 만들고 싶은 임팩트를 설명해 주세요.";
        }
        return "이번 이직(혹은 지원)을 통해 이루고 싶은 성장 방향과, "
                + "기대하는 팀·업무 환경을 " + named(r.latestCompany(), "지금까지의 경험") + "과 연결해 설명해 주세요.";
    }

    private List<String> projectPrompts(MockInterviewResumeContext r, List<String> jdGap, int seed) {
        String p1 = r.latestProjectName();
        String p2 = r.secondProjectName();
        String tech = r.latestProjectTech();
        String company = r.latestCompany();
        String gapSkill = jdGap.isEmpty() ? tech : jdGap.get(0);

        String anchor1 = !p1.isBlank() ? "「" + p1 + "」" : "최근 프로젝트";
        String anchor2 = !p2.isBlank() ? "「" + p2 + "」" : (!company.isBlank() ? "「" + company + "」 업무" : "다른 프로젝트");

        return List.of(
                anchor1 + "에서 " + blankTo(tech, "핵심 기술") + " 스택을 확정할 때 검토한 대안과 선택 기준을 설명해 주세요.",
                anchor2 + " 진행 중 요구사항·성능·일정 중 하나를 포기해야 했던 순간과, "
                        + "어떤 데이터·논의로 결정했는지 구체적으로 말씀해 주세요.",
                !r.careerHighlight().isBlank()
                        ? r.latestCompany() + " 재직 중 「" + truncate(r.careerHighlight(), 44) + "」와 관련해 "
                        + "재현·진단·해결 과정을 단계별로 설명해 주세요."
                        : anchor1 + "에서 재현이 어려웠던 장애/버그를 어떻게 추적하고 해결했는지 설명해 주세요.",
                !gapSkill.isBlank()
                        ? anchor1 + " 또는 " + anchor2 + "에서 "
                        + "「" + gapSkill + "」와(과) 연관된 변경의 정량적 임팩트(지표·비용·시간)를 공유해 주세요."
                        : anchor1 + "에서 본인이 주도한 변경의 정량적 임팩트(지표·비용·시간)를 공유해 주세요."
        );
    }

    private List<String> domainSkillAnchors(List<String> resumeSkills, Set<String> jdSkills, List<String> jdGap) {
        List<String> anchors = new ArrayList<>();
        for (String s : resumeSkills) {
            if (jdSkills.contains(JobSkillNormalizer.canonicalLower(s))) {
                anchors.add(s);
            }
        }
        for (String g : jdGap) {
            if (!anchors.contains(g)) {
                anchors.add(g);
            }
        }
        for (String s : resumeSkills) {
            if (!anchors.contains(s)) {
                anchors.add(s);
            }
        }
        return anchors.stream().limit(6).toList();
    }

    private String domainPrompt(
            String topic,
            String skill,
            MockInterviewResumeContext r,
            String companyName,
            int seed
    ) {
        String skillPart = skill.isBlank() ? topic : "「" + skill + "」";
        String company = blankTo(companyName, "");
        String[] variants = {
                skillPart + "을(를) " + topic + " 관점에서 실무에 적용한 경험을, "
                        + "아키텍처·코드·운영 중 하나를 골라 깊게 설명해 주세요.",
                !r.latestProjectName().isBlank()
                        ? "「" + r.latestProjectName() + "」에서 " + skillPart + "와(과) 관련된 "
                        + topic + " 이슈를 어떻게 다루었는지 설명해 주세요."
                        : topic + "와(과) 관련해 본인이 가장 깊게 파고든 기술 경험을 사례와 함께 설명해 주세요.",
                !company.isBlank()
                        ? company + " 포지션에서 요구될 " + skillPart + " 역량을, "
                        + named(r.latestCompany(), "현재/이전") + " 경험과 연결해 설명해 주세요."
                        : skillPart + " 관련 " + topic + "에서 면접관이 검증할 만한 디테일을 짚어가며 설명해 주세요."
        };
        return variants[seed % variants.length];
    }

    private CoreCsSlot coreCsForCategory(
            JobPostingCategory category,
            MockInterviewResumeContext r,
            MockInterviewJdContext jd
    ) {
        String anchor = !r.latestProjectName().isBlank() ? "「" + r.latestProjectName() + "」" : "실무 프로젝트";
        if (category == JobPostingCategory.BACKEND || category == JobPostingCategory.FULLSTACK) {
            return new CoreCsSlot(
                    "API·DB·동시성",
                    anchor + "에서 API 설계·트랜잭션·동시성(락, 격리, idempotency) 중 하나를 골라 "
                            + "문제 상황과 해결 과정을 설명해 주세요.",
                    List.of("transaction", "concurrency", "idempotency")
            );
        }
        if (category == JobPostingCategory.DEVOPS) {
            return new CoreCsSlot(
                    "배포·관측성",
                    anchor + " 또는 " + named(r.latestCompany(), "팀") + " 환경에서 "
                            + "배포 파이프라인·관측성·장애 대응 중 하나를 깊게 설명해 주세요.",
                    List.of("cicd", "observability", "slo")
            );
        }
        if (category == JobPostingCategory.AI_ML) {
            return new CoreCsSlot(
                    "모델·데이터 파이프라인",
                    anchor + "와(과) 연관된 데이터/모델 평가·배포·비용 트레이드오프 경험을 설명해 주세요.",
                    List.of("evaluation", "pipeline", "inference")
            );
        }
        return new CoreCsSlot(
                "Browser Internals & Rendering Pipeline",
                "브라우저 렌더링 파이프라인(파싱→스타일→레이아웃→페인트→컴포지트)에서 비용이 큰 구간과, "
                        + anchor + "에서 실제로 최적화한 사례를 설명해 주세요.",
                List.of("renderingPipeline", "reflow")
        );
    }

    private String csInfraPrompt(
            String topic,
            MockInterviewResumeContext r,
            List<String> jdGap,
            boolean jdOnly,
            int seed
    ) {
        String gapHint = jdOnly && !jdGap.isEmpty()
                ? " (공고에는 있으나 이력서에 약한 「" + jdGap.get(0) + "」 관점 포함) "
                : " ";
        String anchor = !r.latestProjectName().isBlank() ? "「" + r.latestProjectName() + "」" : "실무";
        String[] variants = {
                topic + " 관련" + gapHint + "경험을 " + anchor + " 사례와 연결해, "
                        + "설계 선택·장애·지표 중 하나를 깊게 설명해 주세요.",
                topic + "를 다룰 때 본인이 실제로 사용한 도구·패턴과, "
                        + "팀에 설득한 근거를 " + anchor + " 맥락에서 설명해 주세요."
        };
        return variants[seed % variants.length];
    }

    private String behavioralPrompt(MockInterviewResumeContext r, String companyName, int seed) {
        String company = blankTo(companyName, "");
        String[] variants = {
                !r.latestCompany().isBlank()
                        ? "「" + r.latestCompany() + "」에서 팀과 의견이 갈렸을 때 "
                        + "합의에 이른 사례를 상황·역할·행동·결과로 설명해 주세요."
                        : "협업 중 갈등이나 우선순위 충돌을 해결한 사례를 STAR 구조로 공유해 주세요.",
                !company.isBlank()
                        ? company + "에서 일할 때 DX·코드 품질·프로세스 중 하나를 개선한 경험을 공유해 주세요."
                        : "개발자 경험(DX)이나 팀 생산성을 개선한 구체적 사례를 공유해 주세요.",
                !r.latestProjectName().isBlank()
                        ? "「" + r.latestProjectName() + "」에서 일정/품질 압박 속에서 "
                        + "본인이 내린 판단과 그 결과를 설명해 주세요."
                        : "압박 상황에서 본인의 판단 기준과 결과를 STAR 구조로 공유해 주세요."
        };
        return variants[seed % variants.length];
    }

    private static String named(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : "「" + value + "」";
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max) + "…";
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
