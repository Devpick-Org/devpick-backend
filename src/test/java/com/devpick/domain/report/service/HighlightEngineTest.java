package com.devpick.domain.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HighlightEngineTest {

    private HighlightEngine engine;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        engine = new HighlightEngine(objectMapper);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String dailyJson(int... counts) {
        String[] days = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (int i = 0; i < days.length; i++) {
            list.add(Map.of("dayOfWeek", days[i], "count", i < counts.length ? counts[i] : 0));
        }
        return toJson(list);
    }

    private String topTagsJson(String tag, int count) {
        return toJson(List.of(Map.of("tag", tag, "count", count)));
    }

    private String prevJson(int contentsRead, int questionsCreated, int jobPostingsViewed) {
        return toJson(Map.of("contentsRead", contentsRead, "questionsCreated", questionsCreated,
                "jobPostingsViewed", jobPostingsViewed));
    }

    private String questionAnalysisJson(int techTotal, int techResolved, int careerTotal, int careerResolved) {
        return toJson(Map.of(
                "tech", Map.of("total", techTotal, "resolved", techResolved, "keywords", List.of()),
                "career", Map.of("total", careerTotal, "resolved", careerResolved, "keywords", List.of())
        ));
    }

    private String contentKeywordsJson(List<String> keywords, int matchRate) {
        List<Map<String, Object>> kws = keywords.stream()
                .map(k -> Map.<String, Object>of("keyword", k, "count", 1))
                .toList();
        return toJson(Map.of("keywords", kws, "interestTagMatchRate", matchRate));
    }

    private String jobTechStacksJson(String... techs) {
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (String t : techs) list.add(Map.of("tech", t, "count", 1));
        return toJson(list);
    }

    private HighlightEngine.HighlightInput baseInput(
            int contentsRead, int questionsCreated, int jobPostingsViewed) {
        return new HighlightEngine.HighlightInput(
                contentsRead, questionsCreated, jobPostingsViewed,
                topTagsJson("Java", contentsRead),
                dailyJson(contentsRead, 0, 0, 0, 0, 0, 0),
                prevJson(0, 0, 0),
                "[]",
                contentKeywordsJson(List.of(), 0),
                questionAnalysisJson(questionsCreated, 0, 0, 0)
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parse(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @Test
    @DisplayName("항상 success·info·warning 카드 3개를 반환한다")
    void generate_alwaysReturns3Cards() {
        String result = engine.generate(baseInput(5, 2, 0));
        List<Map<String, String>> cards = parse(result);

        assertThat(cards).hasSize(3);
        assertThat(cards.get(0).get("type")).isEqualTo("success");
        assertThat(cards.get(1).get("type")).isEqualTo("info");
        assertThat(cards.get(2).get("type")).isEqualTo("warning");
    }

    @Test
    @DisplayName("활동 없을 때도 fallback 카드 3개 반환")
    void generate_noActivity_returnsFallback() {
        String result = engine.generate(baseInput(0, 0, 0));
        List<Map<String, String>> cards = parse(result);
        assertThat(cards).hasSize(3);
        cards.forEach(c -> {
            assertThat(c.get("title")).isNotBlank();
            assertThat(c.get("description")).isNotBlank();
        });
    }

    @Test
    @DisplayName("success — 질문 2개 이상 + 채택 1개 이상이면 채택 카드")
    void successCard_questionAdopted() {
        HighlightEngine.HighlightInput input = new HighlightEngine.HighlightInput(
                3, 3, 0,
                topTagsJson("Java", 3),
                dailyJson(3, 0, 0, 0, 0, 0, 0),
                prevJson(0, 0, 0),
                "[]",
                contentKeywordsJson(List.of(), 0),
                questionAnalysisJson(2, 1, 1, 1)
        );
        List<Map<String, String>> cards = parse(engine.generate(input));
        assertThat(cards.get(0).get("title")).contains("채택");
    }

    @Test
    @DisplayName("success — 주 4일 이상 + 읽은 글 3편 이상이면 꾸준한 루틴 카드")
    void successCard_consistentRoutine() {
        HighlightEngine.HighlightInput input = new HighlightEngine.HighlightInput(
                4, 0, 0,
                topTagsJson("React", 4),
                dailyJson(1, 1, 1, 1, 0, 0, 0),
                prevJson(0, 0, 0),
                "[]",
                contentKeywordsJson(List.of(), 0),
                questionAnalysisJson(0, 0, 0, 0)
        );
        List<Map<String, String>> cards = parse(engine.generate(input));
        assertThat(cards.get(0).get("title")).contains("일");
    }

    @Test
    @DisplayName("info — 읽은 글 키워드와 공고 기술 스택 교집합이 있으면 연결 카드")
    void infoCard_keywordJobOverlap() {
        HighlightEngine.HighlightInput input = new HighlightEngine.HighlightInput(
                5, 0, 3,
                topTagsJson("Java", 5),
                dailyJson(5, 0, 0, 0, 0, 0, 0),
                prevJson(0, 0, 0),
                jobTechStacksJson("Java", "Spring"),
                contentKeywordsJson(List.of("java", "Spring Boot"), 0),
                questionAnalysisJson(0, 0, 0, 0)
        );
        List<Map<String, String>> cards = parse(engine.generate(input));
        assertThat(cards.get(1).get("title")).contains("등장했어요");
    }

    @Test
    @DisplayName("warning — 3일 이상 연속 비활동이면 비활동 구간 카드")
    void warningCard_consecutiveInactiveDays() {
        HighlightEngine.HighlightInput input = new HighlightEngine.HighlightInput(
                3, 0, 0,
                topTagsJson("Java", 3),
                dailyJson(2, 1, 0, 0, 0, 0, 0),
                prevJson(0, 0, 0),
                "[]",
                contentKeywordsJson(List.of(), 0),
                questionAnalysisJson(0, 0, 0, 0)
        );
        List<Map<String, String>> cards = parse(engine.generate(input));
        assertThat(cards.get(2).get("title")).contains("일간 활동이 없었어요");
    }

    @Test
    @DisplayName("warning — 질문 없고 읽은 글 3편 이상이면 질문 독려 카드")
    void warningCard_noQuestionWithContent() {
        // MON·WED·FRI·SUN 활동 → 연속 비활동 2일 이하, 연속 비활동 3일 규칙 미발동
        HighlightEngine.HighlightInput input = new HighlightEngine.HighlightInput(
                4, 0, 0,
                topTagsJson("Python", 4),
                dailyJson(1, 0, 1, 0, 1, 0, 1),
                prevJson(0, 0, 0),
                "[]",
                contentKeywordsJson(List.of(), 0),
                questionAnalysisJson(0, 0, 0, 0)
        );
        List<Map<String, String>> cards = parse(engine.generate(input));
        assertThat(cards.get(2).get("title")).contains("질문");
    }

    @Test
    @DisplayName("warning — 전주보다 읽은 글 줄었으면 감소 카드")
    void warningCard_decreasedFromPrevWeek() {
        // MON·WED·FRI 활동 → 연속 비활동 최대 1일, 연속 비활동 3일 규칙 미발동
        HighlightEngine.HighlightInput input = new HighlightEngine.HighlightInput(
                2, 1, 0,
                topTagsJson("Java", 2),
                dailyJson(1, 0, 1, 0, 1, 0, 0),
                prevJson(7, 2, 0),
                "[]",
                contentKeywordsJson(List.of(), 0),
                questionAnalysisJson(1, 0, 0, 0)
        );
        List<Map<String, String>> cards = parse(engine.generate(input));
        assertThat(cards.get(2).get("title")).contains("줄었어요");
    }

    @Test
    @DisplayName("모든 카드의 title·description이 비어있지 않다")
    void allCards_titleAndDescriptionNonBlank() {
        String result = engine.generate(new HighlightEngine.HighlightInput(
                7, 3, 4,
                topTagsJson("Spring", 7),
                dailyJson(3, 2, 0, 0, 0, 1, 1),
                prevJson(5, 1, 2),
                jobTechStacksJson("Spring", "MySQL"),
                contentKeywordsJson(List.of("Spring Boot", "JPA"), 75),
                questionAnalysisJson(2, 1, 1, 0)
        ));
        parse(result).forEach(c -> {
            assertThat(c.get("title")).isNotBlank();
            assertThat(c.get("description")).isNotBlank();
        });
    }

    @Test
    @DisplayName("success — 전주보다 읽은 글 늘었으면 증가 카드 (Rule 4)")
    void successCard_prevWeekIncrease() {
        // activeDays=2 (<4, Rule2 fail), contentsRead=4 (<5, Rule3 fail), questionsCreated=0 (Rule1 fail)
        // prevContents=2 < 4 → Rule4 fires
        HighlightEngine.HighlightInput input = new HighlightEngine.HighlightInput(
                4, 0, 0,
                topTagsJson("Java", 4),
                dailyJson(2, 2, 0, 0, 0, 0, 0),
                prevJson(2, 0, 0),
                "[]",
                contentKeywordsJson(List.of(), 0),
                questionAnalysisJson(0, 0, 0, 0)
        );
        List<Map<String, String>> cards = parse(engine.generate(input));
        assertThat(cards.get(0).get("title")).contains("더 읽었어요");
    }

    @Test
    @DisplayName("info — 관심태그 매칭률 60% 이상이면 매칭 카드 (Rule 3)")
    void infoCard_interestTagMatchRate() {
        // no keyword overlap (empty keywords), all days equal (ratio<30%, Rule2 fail), matchRate=70 → Rule3 fires
        // success: contentsRead=2<3 (Rule2 fail), <5 (Rule3 fail), prev=0 (Rule4 fail) → fallback
        HighlightEngine.HighlightInput input = new HighlightEngine.HighlightInput(
                2, 0, 0,
                topTagsJson("Java", 2),
                dailyJson(1, 1, 1, 1, 1, 1, 1),
                prevJson(0, 0, 0),
                "[]",
                contentKeywordsJson(List.of(), 70),
                questionAnalysisJson(0, 0, 0, 0)
        );
        List<Map<String, String>> cards = parse(engine.generate(input));
        assertThat(cards.get(1).get("title")).contains("매칭률");
    }

    @Test
    @DisplayName("info — 채용 공고 3건 이상 탐색하면 공고 탐색 카드 (Rule 4)")
    void infoCard_jobPostingsViewed() {
        // no overlap (empty keywords), daily all equal (ratio<30%), matchRate=0 → Rules 1-3 fail
        // jobPostingsViewed=5 → Rule 4 fires
        HighlightEngine.HighlightInput input = new HighlightEngine.HighlightInput(
                2, 0, 5,
                topTagsJson("Java", 2),
                dailyJson(1, 1, 1, 1, 1, 0, 0),
                prevJson(0, 0, 0),
                "[]",
                contentKeywordsJson(List.of(), 0),
                questionAnalysisJson(0, 0, 0, 0)
        );
        List<Map<String, String>> cards = parse(engine.generate(input));
        assertThat(cards.get(1).get("title")).contains("채용 공고");
    }

    @Test
    @DisplayName("warning — topTag 비율 70% 이상이면 편중 카드 (Rule 4) + computeTopTagRatio 다중 태그")
    void warningCard_topTagBias() {
        // no streak (all days active), questionsCreated=1 (Rule2 fail), prevContents=0 (Rule3 fail)
        // topTagRatio: Java=8, React=2 → 8/10=0.8 ≥ 0.7 → Rule4 fires
        String multiTopTags = toJson(List.of(
                Map.of("tag", "Java", "count", 8),
                Map.of("tag", "React", "count", 2)
        ));
        HighlightEngine.HighlightInput input = new HighlightEngine.HighlightInput(
                4, 1, 0,
                multiTopTags,
                dailyJson(1, 1, 1, 1, 1, 1, 1),
                prevJson(0, 0, 0),
                "[]",
                contentKeywordsJson(List.of(), 0),
                questionAnalysisJson(1, 0, 0, 0)
        );
        List<Map<String, String>> cards = parse(engine.generate(input));
        assertThat(cards.get(2).get("title")).contains("집중됐어요");
    }

    @Test
    @DisplayName("warning — 읽은 글 2편 이하이면 학습량 부족 카드 (Rule 5)")
    void warningCard_lowContent() {
        // no streak, questionsCreated=1 (Rule2 fail), prevContents=0 (Rule3 fail)
        // topTagRatio: Java=3,React=3 → 0.5 < 0.7 (Rule4 fail)
        // contentsRead=1 ≤ 2 → Rule5 fires
        String equalTags = toJson(List.of(
                Map.of("tag", "Java", "count", 3),
                Map.of("tag", "React", "count", 3)
        ));
        HighlightEngine.HighlightInput input = new HighlightEngine.HighlightInput(
                1, 1, 0,
                equalTags,
                dailyJson(1, 0, 1, 0, 1, 1, 1),
                prevJson(0, 0, 0),
                "[]",
                contentKeywordsJson(List.of(), 0),
                questionAnalysisJson(1, 0, 0, 0)
        );
        List<Map<String, String>> cards = parse(engine.generate(input));
        assertThat(cards.get(2).get("title")).contains("학습량이 적었어요");
    }

    @Test
    @DisplayName("info — 키워드가 공고 기술스택을 부분 포함하면 교집합 카드 (partial match)")
    void infoCard_keywordPartialMatch() {
        // "Spring Boot" keyword vs "spring" tech → lower.contains(tech) = true → overlap found
        HighlightEngine.HighlightInput input = new HighlightEngine.HighlightInput(
                5, 0, 2,
                topTagsJson("Spring", 5),
                dailyJson(5, 0, 0, 0, 0, 0, 0),
                prevJson(0, 0, 0),
                jobTechStacksJson("spring"),
                contentKeywordsJson(List.of("Spring Boot", "JPA"), 0),
                questionAnalysisJson(0, 0, 0, 0)
        );
        List<Map<String, String>> cards = parse(engine.generate(input));
        assertThat(cards.get(1).get("title")).contains("등장했어요");
    }

    @Test
    @DisplayName("null JSON 입력 시 fallback 카드 3개 반환 — null 분기 커버")
    void nullJsonFields_returnsFallbackCards() {
        HighlightEngine.HighlightInput input = new HighlightEngine.HighlightInput(
                0, 0, 0, null, null, null, null, null, null
        );
        List<Map<String, String>> cards = parse(engine.generate(input));
        assertThat(cards).hasSize(3);
        cards.forEach(c -> {
            assertThat(c.get("title")).isNotBlank();
            assertThat(c.get("description")).isNotBlank();
        });
    }
}
