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
}
