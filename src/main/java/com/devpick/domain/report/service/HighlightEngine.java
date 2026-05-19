package com.devpick.domain.report.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class HighlightEngine {

    private static final Map<String, String> DAY_KOREAN = Map.of(
            "MON", "월요일", "TUE", "화요일", "WED", "수요일",
            "THU", "목요일", "FRI", "금요일", "SAT", "토요일", "SUN", "일요일"
    );

    private final ObjectMapper objectMapper;

    public record HighlightInput(
            int contentsRead,
            int questionsCreated,
            int jobPostingsViewed,
            String topTagsJson,
            String dailyActivitiesJson,
            String prevWeekComparisonJson,
            String jobTechStacksJson,
            String contentKeywordsJson,
            String questionAnalysisJson
    ) {}

    private record DayStats(String dayName, int ratio) {}
    private record InactiveStreak(int days, String startDay, String endDay) {}

    public String generate(HighlightInput input) {
        List<Map<String, String>> cards = List.of(
                buildSuccessCard(input),
                buildInfoCard(input),
                buildWarningCard(input)
        );
        try {
            return objectMapper.writeValueAsString(cards);
        } catch (Exception e) {
            log.warn("[HighlightEngine] 직렬화 실패: {}", e.getMessage());
            return "[]";
        }
    }

    private Map<String, String> buildSuccessCard(HighlightInput in) {
        int totalResolved = parseTotalResolved(in.questionAnalysisJson());
        // Rule 1: 질문 전부 채택
        if (in.questionsCreated() >= 1 && totalResolved == in.questionsCreated()) {
            return card("success",
                    String.format("%d개 질문 모두 답변이 채택됐어요", totalResolved),
                    "완벽한 한 주네요. 좋은 질문이 좋은 답변을 만들었어요.");
        }
        // Rule 2: 질문 부분 채택
        if (in.questionsCreated() >= 2 && totalResolved >= 1) {
            return card("success",
                    String.format("%d개 질문 중 %d개에 답변이 채택됐어요", in.questionsCreated(), totalResolved),
                    "좋은 질문을 남겼네요. 답변을 통해 확실히 이해하는 학습을 이어가고 있어요.");
        }
        // Rule 3: 기술 + 커리어 질문 모두 작성
        if (hasBothQuestionTypes(in.questionAnalysisJson())) {
            return card("success", "기술 질문과 커리어 질문을 모두 남겼어요",
                    "성장과 취업, 두 방향을 동시에 고민하고 있어요. 균형 잡힌 개발자로 성장하고 있어요.");
        }
        // Rule 4: 활동 요일 꾸준함
        int activeDays = countActiveDays(in.dailyActivitiesJson());
        if (activeDays >= 5) {
            return card("success",
                    String.format("주 %d일, 거의 매일 학습했어요", activeDays),
                    String.format("%d편의 글을 꾸준히 읽었어요. 이번 주 루틴이 완벽했어요.", in.contentsRead()));
        }
        if (activeDays >= 4 && in.contentsRead() >= 3) {
            return card("success",
                    String.format("주 %d일 고르게 활동하며 꾸준한 루틴을 만들었어요", activeDays),
                    String.format("%d편의 글을 꾸준히 읽었어요. 이번 주도 수고했습니다.", in.contentsRead()));
        }
        // Rule 5: 읽은 글 많음 + topTag
        if (in.contentsRead() >= 5) {
            String topTag = parseTopTag(in.topTagsJson());
            String title = topTag != null
                    ? String.format("이번 주 %s 글 위주로 %d편을 읽었어요", topTag, in.contentsRead())
                    : String.format("이번 주 %d편의 글을 읽었어요", in.contentsRead());
            return card("success", title, "집중 학습이 실력이 됩니다. 이번 주도 수고했습니다.");
        }
        // Rule 6: 전주 대비 읽은 글 증가
        int prevContents = parsePrevField(in.prevWeekComparisonJson(), "contentsRead");
        if (in.contentsRead() > prevContents && prevContents > 0) {
            return card("success",
                    String.format("지난 주보다 %d편 더 읽었어요", in.contentsRead() - prevContents),
                    "성장세가 느껴져요. 이 흐름을 다음 주에도 이어가보세요.");
        }
        // Rule 7: 전주 대비 질문 증가
        int prevQuestions = parsePrevField(in.prevWeekComparisonJson(), "questionsCreated");
        if (in.questionsCreated() > prevQuestions && prevQuestions >= 0 && in.questionsCreated() >= 1) {
            return card("success",
                    String.format("지난 주보다 질문을 %d개 더 남겼어요", in.questionsCreated() - prevQuestions),
                    "적극적인 질문이 학습 깊이를 높여요. 커뮤니티가 함께 성장하고 있어요.");
        }
        // Rule 8: 채용 공고 적극 탐색
        if (in.jobPostingsViewed() >= 5) {
            return card("success",
                    String.format("채용 공고 %d건을 탐색했어요", in.jobPostingsViewed()),
                    "취업 준비를 열심히 하고 있어요. 학습과 병행하면 더 좋은 결과가 있을 거예요.");
        }
        // Fallback
        if (in.contentsRead() > 0 || in.questionsCreated() > 0) {
            return card("success", "이번 주도 학습을 이어갔어요",
                    "꾸준함이 실력이 됩니다. 이번 주도 수고했습니다.");
        }
        return card("success", "플랫폼을 방문했어요",
                "작은 시작이 큰 변화의 시작이에요. 다음 주엔 글 한 편부터 읽어보세요.");
    }

    private Map<String, String> buildInfoCard(HighlightInput in) {
        // Rule 1: contentKeywords ∩ jobTechStacks 교집합
        String overlap = findKeywordOverlap(in.contentKeywordsJson(), in.jobTechStacksJson());
        if (overlap != null) {
            return card("info",
                    String.format("읽은 글과 탐색한 공고 모두에서 %s가 등장했어요", overlap),
                    "학습 방향과 취업 준비가 잘 연결되고 있어요. 이 흐름을 유지해보세요.");
        }
        // Rule 2: 가장 활동 많은 요일 + 비율
        DayStats best = findMostActiveDay(in.dailyActivitiesJson());
        if (best != null && best.ratio() >= 30) {
            String dayKo = DAY_KOREAN.getOrDefault(best.dayName(), best.dayName());
            return card("info",
                    String.format("%s에 가장 집중해서 학습했어요", dayKo),
                    String.format("이번 주 전체 활동의 %d%%가 %s에 집중됐어요. 패턴이 뚜렷하게 잡혔네요.",
                            best.ratio(), dayKo));
        }
        // Rule 3: 관심 태그 매칭률
        int matchRate = parseInterestTagMatchRate(in.contentKeywordsJson());
        if (matchRate >= 60) {
            return card("info",
                    String.format("관심 태그와 이번 주 학습의 매칭률이 %d%%예요", matchRate),
                    "내가 원하는 방향으로 학습이 잘 이어지고 있어요.");
        }
        // Rule 4: 채용 공고 탐색
        if (in.jobPostingsViewed() >= 3) {
            return card("info",
                    String.format("채용 공고 %d건을 탐색했어요", in.jobPostingsViewed()),
                    "콘텐츠 학습과 채용 탐색을 함께 병행하고 있어요. 좋은 흐름이에요.");
        }
        // Rule 5: 전주 대비 공고 탐색 증가
        int prevJobs = parsePrevField(in.prevWeekComparisonJson(), "jobPostingsViewed");
        if (in.jobPostingsViewed() > prevJobs && in.jobPostingsViewed() >= 1) {
            return card("info",
                    String.format("지난 주보다 채용 공고 탐색이 늘었어요", in.jobPostingsViewed()),
                    "취업 준비에 점점 더 적극적으로 임하고 있어요.");
        }
        // Rule 6: 주말에도 학습
        if (hasWeekendActivity(in.dailyActivitiesJson()) && in.contentsRead() >= 2) {
            return card("info", "주말에도 학습을 멈추지 않았어요",
                    "쉬는 날에도 학습하는 습관이 장기적으로 큰 차이를 만들어요.");
        }
        // Rule 7: topTag 있음
        String topTag = parseTopTag(in.topTagsJson());
        if (topTag != null) {
            return card("info",
                    String.format("이번 주는 %s 위주로 학습했어요", topTag),
                    "집중 학습은 깊이를 만들어요. 관련 프로젝트에도 도전해보세요.");
        }
        return card("info", "이번 주 학습 기록을 분석했어요",
                "꾸준히 활동하면 더 풍부한 인사이트를 얻을 수 있어요.");
    }

    private Map<String, String> buildWarningCard(HighlightInput in) {
        // Rule 1: 연속 비활동일
        InactiveStreak streak = findLongestInactiveStreak(in.dailyActivitiesJson());
        if (streak != null && streak.days() >= 3) {
            String startKo = DAY_KOREAN.getOrDefault(streak.startDay(), streak.startDay());
            String endKo = DAY_KOREAN.getOrDefault(streak.endDay(), streak.endDay());
            return card("warning",
                    String.format("%s부터 %s까지 %d일간 활동이 없었어요", startKo, endKo, streak.days()),
                    "주 후반 루틴이 아직 자리잡지 않은 것 같아요. 하루 10분이라도 시작해보는 건 어떨까요?");
        }
        // Rule 2: 주중 활동이 전혀 없고 주말에만 활동
        if (isWeekdayInactive(in.dailyActivitiesJson()) && hasWeekendActivity(in.dailyActivitiesJson())) {
            return card("warning", "주말에만 집중적으로 활동했어요",
                    "평일 루틴을 만들면 더 꾸준한 성장이 가능해요. 하루 10분씩 짧게라도 시작해보세요.");
        }
        // Rule 3: 질문 0개 + 읽은 글 있음
        if (in.questionsCreated() == 0 && in.contentsRead() >= 3) {
            return card("warning", "글은 꾸준히 읽었지만 질문이 없었어요",
                    "학습하면서 궁금한 점이 생겼다면 질문으로 남겨보세요. 답변을 통해 더 깊이 이해할 수 있어요.");
        }
        // Rule 4: 전주 대비 읽은 글 감소
        int prevContents = parsePrevField(in.prevWeekComparisonJson(), "contentsRead");
        if (prevContents > 0 && in.contentsRead() < prevContents) {
            return card("warning",
                    String.format("지난 주보다 읽은 글이 %d편 줄었어요", prevContents - in.contentsRead()),
                    "바쁜 주였을 수 있어요. 다음 주엔 하루 1편을 목표로 다시 시작해보는 건 어떨까요?");
        }
        // Rule 5: 전주 대비 질문 감소
        int prevQuestions = parsePrevField(in.prevWeekComparisonJson(), "questionsCreated");
        if (prevQuestions >= 2 && in.questionsCreated() < prevQuestions) {
            return card("warning",
                    String.format("지난 주보다 질문이 %d개 줄었어요", prevQuestions - in.questionsCreated()),
                    "질문하는 습관을 유지하면 학습 깊이가 달라져요. 다음 주엔 다시 도전해보세요.");
        }
        // Rule 6: topTag 편중
        if (computeTopTagRatio(in.topTagsJson()) >= 0.7) {
            String topTag = parseTopTag(in.topTagsJson());
            return card("warning",
                    String.format("%s에만 집중됐어요", topTag != null ? topTag : "한 분야"),
                    "깊이도 중요하지만 다양한 분야를 함께 탐색하면 더 폭넓은 시야를 가질 수 있어요.");
        }
        // Rule 7: 채용 공고 미탐색
        if (in.jobPostingsViewed() == 0 && in.contentsRead() >= 3) {
            return card("warning", "채용 공고를 한 건도 보지 않았어요",
                    "학습도 중요하지만 목표를 확인하는 것도 동기부여가 돼요. 관심 공고를 한 번 살펴보세요.");
        }
        // Rule 8: 읽은 글 적음
        if (in.contentsRead() <= 2) {
            return card("warning", "이번 주 학습량이 적었어요",
                    "바쁜 주였더라도 괜찮아요. 다음 주엔 하루 1편씩 읽는 것부터 시작해보세요.");
        }
        if (in.questionsCreated() == 0) {
            return card("warning", "질문 참여가 아직 없어요",
                    "궁금한 점을 질문으로 남기면 커뮤니티와 함께 성장할 수 있어요.");
        }
        return card("warning", "다음 주도 꾸준한 학습을 이어가요",
                "이번 주 활동을 바탕으로 다음 주엔 더 깊이 있는 학습에 도전해보세요.");
    }

    // --- Helpers ---

    private Map<String, String> card(String type, String title, String description) {
        return Map.of("type", type, "title", title, "description", description);
    }

    private int countActiveDays(String json) {
        return (int) parseList(json).stream()
                .filter(m -> ((Number) m.getOrDefault("count", 0)).intValue() > 0)
                .count();
    }

    private String parseTopTag(String json) {
        List<Map<String, Object>> tags = parseList(json);
        if (tags.isEmpty()) return null;
        Object tag = tags.get(0).get("tag");
        return tag != null ? tag.toString() : null;
    }

    private double computeTopTagRatio(String json) {
        List<Map<String, Object>> tags = parseList(json);
        if (tags.isEmpty()) return 0.0;
        if (tags.size() == 1) return 1.0;
        long total = tags.stream().mapToLong(t -> ((Number) t.getOrDefault("count", 0)).longValue()).sum();
        if (total == 0) return 0.0;
        return (double) ((Number) tags.get(0).getOrDefault("count", 0)).longValue() / total;
    }

    private int parseTotalResolved(String json) {
        try {
            if (json == null || json.isBlank()) return 0;
            Map<String, Map<String, Object>> map = objectMapper.readValue(json, new TypeReference<>() {});
            int tech = ((Number) map.getOrDefault("tech", Map.of()).getOrDefault("resolved", 0)).intValue();
            int career = ((Number) map.getOrDefault("career", Map.of()).getOrDefault("resolved", 0)).intValue();
            return tech + career;
        } catch (Exception e) {
            return 0;
        }
    }

    private int parsePrevField(String json, String field) {
        try {
            if (json == null || json.isBlank()) return 0;
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            Object val = map.get(field);
            return val != null ? ((Number) val).intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private int parseInterestTagMatchRate(String json) {
        try {
            if (json == null || json.isBlank()) return 0;
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            Object rate = map.get("interestTagMatchRate");
            return rate != null ? ((Number) rate).intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private String findKeywordOverlap(String contentKeywordsJson, String jobTechStacksJson) {
        try {
            if (contentKeywordsJson == null || jobTechStacksJson == null) return null;
            Map<String, Object> ckMap = objectMapper.readValue(contentKeywordsJson, new TypeReference<>() {});
            List<Map<String, Object>> keywords = (List<Map<String, Object>>) ckMap.getOrDefault("keywords", List.of());
            List<Map<String, Object>> techStacks = parseList(jobTechStacksJson);
            if (keywords.isEmpty() || techStacks.isEmpty()) return null;

            Set<String> techSet = techStacks.stream()
                    .map(t -> t.getOrDefault("tech", "").toString().toLowerCase())
                    .collect(Collectors.toSet());

            for (Map<String, Object> kw : keywords) {
                String keyword = kw.getOrDefault("keyword", "").toString();
                String lower = keyword.toLowerCase();
                if (techSet.contains(lower)) return keyword;
                for (String tech : techSet) {
                    if (!tech.isBlank() && (lower.contains(tech) || tech.contains(lower))) return keyword;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private DayStats findMostActiveDay(String json) {
        List<Map<String, Object>> activities = parseList(json);
        long total = activities.stream()
                .mapToLong(m -> ((Number) m.getOrDefault("count", 0)).longValue()).sum();
        if (total == 0) return null;
        Map<String, Object> best = activities.stream()
                .max(Comparator.comparingLong(m -> ((Number) m.getOrDefault("count", 0)).longValue()))
                .orElse(null);
        if (best == null) return null;
        long bestCount = ((Number) best.getOrDefault("count", 0)).longValue();
        int ratio = (int) Math.round(100.0 * bestCount / total);
        return new DayStats(best.get("dayOfWeek").toString(), ratio);
    }

    private InactiveStreak findLongestInactiveStreak(String json) {
        List<Map<String, Object>> activities = parseList(json);
        int maxLen = 0, maxStart = -1, maxEnd = -1;
        int curLen = 0, curStart = -1;
        for (int i = 0; i < activities.size(); i++) {
            int count = ((Number) activities.get(i).getOrDefault("count", 0)).intValue();
            if (count == 0) {
                if (curLen == 0) curStart = i;
                curLen++;
                if (curLen > maxLen) {
                    maxLen = curLen;
                    maxStart = curStart;
                    maxEnd = i;
                }
            } else {
                curLen = 0;
            }
        }
        if (maxLen < 3 || maxStart < 0) return null;
        String startDay = activities.get(maxStart).get("dayOfWeek").toString();
        String endDay = activities.get(maxEnd).get("dayOfWeek").toString();
        return new InactiveStreak(maxLen, startDay, endDay);
    }

    private boolean hasBothQuestionTypes(String json) {
        try {
            if (json == null || json.isBlank()) return false;
            Map<String, Map<String, Object>> map = objectMapper.readValue(json, new TypeReference<>() {});
            int tech = ((Number) map.getOrDefault("tech", Map.of()).getOrDefault("total", 0)).intValue();
            int career = ((Number) map.getOrDefault("career", Map.of()).getOrDefault("total", 0)).intValue();
            return tech > 0 && career > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasWeekendActivity(String json) {
        List<Map<String, Object>> activities = parseList(json);
        return activities.stream()
                .filter(m -> Set.of("SAT", "SUN").contains(String.valueOf(m.getOrDefault("dayOfWeek", ""))))
                .anyMatch(m -> ((Number) m.getOrDefault("count", 0)).intValue() > 0);
    }

    private boolean isWeekdayInactive(String json) {
        List<Map<String, Object>> activities = parseList(json);
        return activities.stream()
                .filter(m -> Set.of("MON", "TUE", "WED", "THU", "FRI").contains(String.valueOf(m.getOrDefault("dayOfWeek", ""))))
                .allMatch(m -> ((Number) m.getOrDefault("count", 0)).intValue() == 0);
    }

    private List<Map<String, Object>> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
