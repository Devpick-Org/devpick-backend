package com.devpick.domain.report.service;

import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.entity.PostType;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.report.client.AiReportClient;
import com.devpick.domain.report.dto.ChartDataResponse;
import com.devpick.domain.report.dto.ReportSummaryResponse;
import com.devpick.domain.report.dto.ShareLinkResponse;
import com.devpick.domain.report.dto.WeeklyReportResponse;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.entity.ReportActivity;
import com.devpick.domain.report.entity.WeeklyReport;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.report.repository.WeeklyReportRepository;
import com.devpick.domain.subscription.entity.PlanType;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.entity.UserTag;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    private static final String[] DAY_NAMES = {"", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
    /** EC2 등 JVM 기본이 UTC일 때도 한국 주(월~일)·월요일 배치가 같은 달력을 쓰도록 */
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final WeeklyReportRepository weeklyReportRepository;
    private final HistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final AiReportClient aiReportClient;
    private final ObjectMapper objectMapper;
    private final WeeklyReportBatchRunner weeklyReportBatchRunner;
    private final PostRepository postRepository;
    private final AnswerRepository answerRepository;
    private final ContentRepository contentRepository;
    private final HighlightEngine highlightEngine;

    // DP-256: 리포트 목록 조회 (드롭다운용 최소 필드)
    @Transactional(readOnly = true)
    public List<ReportSummaryResponse> getReportList(UUID userId) {
        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
        boolean isFree = user.getPlanType() == PlanType.FREE;
        LocalDate cutoff = LocalDate.now(ZONE_SEOUL).minusDays(7);

        return weeklyReportRepository.findByUserIdOrderByWeekStartDesc(userId).stream()
                .map(report -> {
                    boolean locked = isFree && report.getWeekStart() != null
                            && report.getWeekStart().isBefore(cutoff);
                    return ReportSummaryResponse.of(report, locked);
                })
                .toList();
    }

    /**
     * 주간 리포트 홈 — 직전 주(월~일) 배치로 생성된 스냅샷만 반환한다.
     * 이번 주 온디맨드 생성은 하지 않는다(미완 주간이라 수치가 0으로 고정되는 문제 방지).
     */
    @Transactional
    public WeeklyReportResponse getCurrentWeekReport(UUID userId) {
        LocalDate weekStart = getWeekStart(todaySeoul().minusWeeks(1));
        WeeklyReport report = weeklyReportRepository
                .findWithActivitiesByUser_IdAndWeekStart(userId, weekStart)
                .orElseThrow(() -> new DevpickException(ErrorCode.REPORT_NOT_FOUND));
        recordWeeklyReportViewed(userId);
        return toResponse(report);
    }

    // DP-256: 특정 reportId로 리포트 조회
    @Transactional
    public WeeklyReportResponse getReportById(UUID userId, UUID reportId) {
        WeeklyReport report = weeklyReportRepository.findWithActivitiesById(reportId)
                .orElseThrow(() -> new DevpickException(ErrorCode.REPORT_NOT_FOUND));
        if (!report.getUser().getId().equals(userId)) {
            throw new DevpickException(ErrorCode.REPORT_FORBIDDEN);
        }
        recordWeeklyReportViewed(userId);
        return toResponse(report);
    }

    // DP-258: 공유 링크 생성
    @Transactional
    public ShareLinkResponse generateShareLink(UUID userId, UUID reportId) {
        WeeklyReport report = weeklyReportRepository.findById(reportId)
                .orElseThrow(() -> new DevpickException(ErrorCode.REPORT_NOT_FOUND));
        if (!report.getUser().getId().equals(userId)) {
            throw new DevpickException(ErrorCode.REPORT_FORBIDDEN);
        }
        if (report.getShareToken() == null) {
            report.updateShareToken(UUID.randomUUID().toString().replace("-", ""));
        }
        return new ShareLinkResponse(report.getId(), report.getShareToken());
    }

    // DP-258: 공유 링크로 리포트 조회 (비인증)
    @Transactional(readOnly = true)
    public WeeklyReportResponse getReportByShareToken(String token) {
        WeeklyReport report = weeklyReportRepository.findWithActivitiesByShareToken(token)
                .orElseThrow(() -> new DevpickException(ErrorCode.REPORT_NOT_FOUND));
        return toResponse(report);
    }

    // DP-255: 매주 월요일 00:05에 전 주 리포트 자동 생성
    @Scheduled(cron = "0 5 0 * * MON", zone = "Asia/Seoul")
    @Transactional
    public void generateWeeklyReports() {
        LocalDate lastWeekStart = getWeekStart(todaySeoul().minusWeeks(1));
        LocalDate lastWeekEnd = lastWeekStart.plusDays(6);

        List<User> activeUsers = userRepository.findAllByIsActiveTrueAndDeletedAtIsNull();
        log.info("[WeeklyReport] 배치 시작 — 대상 유저: {}명, 주간: {} ~ {}", activeUsers.size(), lastWeekStart, lastWeekEnd);

        int created = 0;
        for (User user : activeUsers) {
            if (weeklyReportRepository.existsByUser_IdAndWeekStart(user.getId(), lastWeekStart)) {
                continue;
            }
            try {
                generateReportForUser(user, lastWeekStart, lastWeekEnd);
                created++;
            } catch (Exception e) {
                log.warn("[WeeklyReport] 유저 {} 리포트 생성 실패: {}", user.getId(), e.getMessage());
            }
        }
        log.info("[WeeklyReport] 배치 완료 — 신규 생성: {}개", created);
    }

    /**
     * 히스토리 최소 일시(없으면 가입일)를 기준으로, 지난 주(월요일 시작)까지 모든 한국 주에 대해
     * 아직 없는 주간 리포트를 생성합니다. 운영 백필용.
     */
    public Map<String, Integer> backfillWeeklyReportsFromHistory() {
        LocalDate lastWeekStart = getWeekStart(todaySeoul().minusWeeks(1));
        List<User> activeUsers = userRepository.findAllByIsActiveTrueAndDeletedAtIsNull();
        int created = 0;
        int usersProcessed = 0;
        log.info("[WeeklyReport] 백필 시작 — 대상 유저: {}명, 마지막 주(월): {}", activeUsers.size(), lastWeekStart);

        for (User u : activeUsers) {
            LocalDate firstWeek = getFirstWeekStartForUser(u);
            if (firstWeek.isAfter(lastWeekStart)) {
                continue;
            }
            usersProcessed++;
            for (LocalDate ws = firstWeek; !ws.isAfter(lastWeekStart); ws = ws.plusWeeks(1)) {
                try {
                    created += weeklyReportBatchRunner.createReportIfAbsent(u.getId(), ws);
                } catch (Exception e) {
                    log.warn("[WeeklyReport] 백필 실패 user={} week={}: {}", u.getId(), ws, e.getMessage());
                }
            }
        }
        log.info("[WeeklyReport] 백필 완료 — 신규 생성: {}건, 주간 대상 유저: {}명", created, usersProcessed);
        return Map.of("created", created, "usersProcessed", usersProcessed);
    }

    /** {@link WeeklyReportBatchRunner}에서 주 단위 트랜잭션으로 호출하기 위한 진입점 */
    public void createWeeklyReportForUser(User user, LocalDate weekStart, LocalDate weekEnd) {
        generateReportForUser(user, weekStart, weekEnd);
    }

    private LocalDate getFirstWeekStartForUser(User user) {
        Optional<LocalDateTime> minH = historyRepository.findMinCreatedAtByUserId(user.getId());
        LocalDate anchor = minH.isPresent()
                ? toSeoulDateFromStoredUtc(minH.get())
                : toSeoulDateFromStoredUtc(user.getCreatedAt());
        return getWeekStart(anchor);
    }

    private LocalDate toSeoulDateFromStoredUtc(LocalDateTime storedUtc) {
        return storedUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(ZONE_SEOUL).toLocalDate();
    }

    // 특정 유저의 주간 리포트 생성 (배치 또는 온디맨드)
    @Transactional
    public WeeklyReportResponse generateOrGetReport(UUID userId, LocalDate weekStart) {
        if (weeklyReportRepository.existsByUser_IdAndWeekStart(userId, weekStart)) {
            WeeklyReport report = weeklyReportRepository
                    .findWithActivitiesByUser_IdAndWeekStart(userId, weekStart)
                    .orElseThrow(() -> new DevpickException(ErrorCode.REPORT_NOT_FOUND));
            return toResponse(report);
        }
        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
        LocalDate weekEnd = weekStart.plusDays(6);
        WeeklyReport report = generateReportForUser(user, weekStart, weekEnd);
        return toResponse(report);
    }

    private WeeklyReport generateReportForUser(User user, LocalDate weekStart, LocalDate weekEnd) {
        ZonedDateTime fromZ = weekStart.atStartOfDay(ZONE_SEOUL);
        ZonedDateTime toZ = weekEnd.atTime(23, 59, 59, 999_999_999).atZone(ZONE_SEOUL);
        LocalDateTime from = LocalDateTime.ofInstant(fromZ.toInstant(), ZoneOffset.UTC);
        LocalDateTime to = LocalDateTime.ofInstant(toZ.toInstant(), ZoneOffset.UTC);

        long contentsRead = historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(
                user.getId(), "content_opened", from, to);
        long questionsCreated = historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(
                user.getId(), "question_created", from, to);
        long jobPostingsViewed = historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(
                user.getId(), "job_posting_viewed", from, to);

        LocalDate prevWeekStart = weekStart.minusWeeks(1);
        LocalDate prevWeekEnd = prevWeekStart.plusDays(6);
        ZonedDateTime prevFromZ = prevWeekStart.atStartOfDay(ZONE_SEOUL);
        ZonedDateTime prevToZ = prevWeekEnd.atTime(23, 59, 59, 999_999_999).atZone(ZONE_SEOUL);
        LocalDateTime prevFrom = LocalDateTime.ofInstant(prevFromZ.toInstant(), ZoneOffset.UTC);
        LocalDateTime prevTo = LocalDateTime.ofInstant(prevToZ.toInstant(), ZoneOffset.UTC);

        long prevContentsRead = historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(
                user.getId(), "content_opened", prevFrom, prevTo);
        long prevQuestionsCreated = historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(
                user.getId(), "question_created", prevFrom, prevTo);
        long prevJobPostingsViewed = historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(
                user.getId(), "job_posting_viewed", prevFrom, prevTo);

        String prevWeekComparisonJson = toJson(Map.of(
                "contentsRead", prevContentsRead,
                "questionsCreated", prevQuestionsCreated,
                "jobPostingsViewed", prevJobPostingsViewed
        ));

        List<Object[]> tagRows = historyRepository.findTopTagsByUserAndPeriod(user.getId(), from, to);
        String topTagsJson = serializeTopTags(tagRows, 3);
        String tagActivitiesJson = serializeTagActivities(tagRows);
        String dailyActivitiesJson = buildDailyActivitiesJson(user.getId(), from, to);
        String jobTechStacksJson = buildJobTechStacksJson(user.getId(), from, to);
        String contentKeywordsJson = buildContentKeywordsJson(user, from, to, tagRows);
        String questionAnalysisJson = buildQuestionAnalysisJson(user.getId(), from, to);

        String highlightsJson = highlightEngine.generate(new HighlightEngine.HighlightInput(
                (int) contentsRead,
                (int) questionsCreated,
                (int) jobPostingsViewed,
                topTagsJson,
                dailyActivitiesJson,
                prevWeekComparisonJson,
                jobTechStacksJson,
                contentKeywordsJson,
                questionAnalysisJson
        ));

        WeeklyReport report = WeeklyReport.builder()
                .user(user)
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .status("generated")
                .build();

        ReportActivity activity = ReportActivity.builder()
                .report(report)
                .contentsRead((int) contentsRead)
                .questionsCreated((int) questionsCreated)
                .jobPostingsViewed((int) jobPostingsViewed)
                .topTags(topTagsJson)
                .prevWeekComparison(prevWeekComparisonJson)
                .dailyActivities(dailyActivitiesJson)
                .tagActivities(tagActivitiesJson)
                .jobTechStacks(jobTechStacksJson)
                .contentKeywords(contentKeywordsJson)
                .questionAnalysis(questionAnalysisJson)
                .highlights(highlightsJson)
                .build();

        report.getActivities().add(activity);
        return weeklyReportRepository.save(report);
    }

    private String buildJobTechStacksJson(UUID userId, LocalDateTime from, LocalDateTime to) {
        try {
            List<Object[]> rows = historyRepository.findJobTechStackFrequencyByUserAndPeriod(userId, from, to);
            List<Map<String, Object>> result = rows.stream()
                    .map(row -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("tech", row[0]);
                        entry.put("count", row[1]);
                        return entry;
                    })
                    .toList();
            return toJson(result);
        } catch (Exception e) {
            log.warn("[WeeklyReport] 공고 기술 스택 집계 실패 userId={}: {}", userId, e.getMessage());
            return "[]";
        }
    }

    private String buildContentKeywordsJson(User user, LocalDateTime from, LocalDateTime to, List<Object[]> tagRows) {
        try {
            List<UUID> contentIds = historyRepository.findReadContentIdsByUserAndPeriod(user.getId(), from, to);
            int interestTagMatchRate = computeInterestTagMatchRate(user, tagRows);

            if (contentIds.isEmpty()) {
                return toJson(Map.of("keywords", List.of(), "interestTagMatchRate", interestTagMatchRate));
            }

            List<Content> contents = contentRepository.findAllById(contentIds);
            List<AiReportClient.ContentItem> items = contents.stream()
                    .map(c -> new AiReportClient.ContentItem(
                            c.getId().toString(),
                            c.getTitle() != null ? c.getTitle() : "",
                            c.getPreview() != null ? c.getPreview() : "",
                            parseTags(c.getTags())
                    ))
                    .toList();

            AiReportClient.ContentKeywordsResponse response =
                    aiReportClient.requestContentKeywords(new AiReportClient.ContentKeywordsRequest(items));

            List<Map<String, Object>> keywords = response != null && response.keywords() != null
                    ? response.keywords().stream()
                            .map(k -> {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("keyword", k.keyword());
                                m.put("count", k.count());
                                return m;
                            })
                            .toList()
                    : List.of();

            return toJson(Map.of("keywords", keywords, "interestTagMatchRate", interestTagMatchRate));
        } catch (Exception e) {
            log.warn("[WeeklyReport] 읽은 글 키워드 분석 실패 userId={}: {}", user.getId(), e.getMessage());
            return null;
        }
    }

    private String buildQuestionAnalysisJson(UUID userId, LocalDateTime from, LocalDateTime to) {
        try {
            List<UUID> postIds = historyRepository.findCreatedPostIdsByUserAndPeriod(userId, from, to);
            if (postIds.isEmpty()) {
                return toJson(Map.of(
                        "tech", Map.of("total", 0, "resolved", 0, "keywords", List.of()),
                        "career", Map.of("total", 0, "resolved", 0, "keywords", List.of())
                ));
            }

            List<Post> posts = postRepository.findAllById(postIds);
            List<Answer> allAnswers = answerRepository.findByPostIdsOrderByCreatedAtAsc(postIds);
            Set<UUID> resolvedPostIds = allAnswers.stream()
                    .filter(Answer::getIsAdopted)
                    .map(a -> a.getPost().getId())
                    .collect(Collectors.toSet());

            List<Post> techPosts = posts.stream().filter(p -> PostType.TECH == p.getPostType()).toList();
            List<Post> careerPosts = posts.stream().filter(p -> PostType.CAREER == p.getPostType()).toList();

            long techResolved = techPosts.stream().filter(p -> resolvedPostIds.contains(p.getId())).count();
            long careerResolved = careerPosts.stream().filter(p -> resolvedPostIds.contains(p.getId())).count();

            Map<String, Answer> adoptedAnswerByPostId = allAnswers.stream()
                    .filter(Answer::getIsAdopted)
                    .collect(Collectors.toMap(a -> a.getPost().getId().toString(), a -> a, (a, b) -> a));

            List<AiReportClient.QuestionItem> techItems = techPosts.stream()
                    .map(p -> new AiReportClient.QuestionItem(
                            p.getTitle(),
                            p.getContent() != null ? p.getContent() : "",
                            adoptedAnswerByPostId.containsKey(p.getId().toString())
                                    ? adoptedAnswerByPostId.get(p.getId().toString()).getContent() : ""
                    ))
                    .toList();

            List<AiReportClient.QuestionItem> careerItems = careerPosts.stream()
                    .map(p -> new AiReportClient.QuestionItem(
                            p.getTitle(),
                            p.getContent() != null ? p.getContent() : "",
                            adoptedAnswerByPostId.containsKey(p.getId().toString())
                                    ? adoptedAnswerByPostId.get(p.getId().toString()).getContent() : ""
                    ))
                    .toList();

            AiReportClient.QuestionKeywordsResponse kwResponse = null;
            if (!techItems.isEmpty() || !careerItems.isEmpty()) {
                kwResponse = aiReportClient.requestQuestionKeywords(
                        new AiReportClient.QuestionKeywordsRequest(techItems, careerItems));
            }

            List<String> techKeywords = kwResponse != null && kwResponse.techKeywords() != null
                    ? kwResponse.techKeywords() : List.of();
            List<String> careerKeywords = kwResponse != null && kwResponse.careerKeywords() != null
                    ? kwResponse.careerKeywords() : List.of();

            return toJson(Map.of(
                    "tech", Map.of("total", techPosts.size(), "resolved", techResolved, "keywords", techKeywords),
                    "career", Map.of("total", careerPosts.size(), "resolved", careerResolved, "keywords", careerKeywords)
            ));
        } catch (Exception e) {
            log.warn("[WeeklyReport] 질문 분석 실패 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    private int computeInterestTagMatchRate(User user, List<Object[]> tagRows) {
        List<String> userTagNames = user.getUserTags().stream()
                .map(ut -> ut.getTag().getName().toLowerCase())
                .toList();
        if (userTagNames.isEmpty()) {
            return 0;
        }
        Set<String> readTagNames = tagRows.stream()
                .map(row -> row[0].toString().toLowerCase())
                .collect(Collectors.toSet());
        long matchCount = userTagNames.stream().filter(readTagNames::contains).count();
        return (int) Math.round(100.0 * matchCount / userTagNames.size());
    }

    @SuppressWarnings("unchecked")
    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(tagsJson, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    // topTags용: {"tag": name, "count": count} 형식, limit 3
    private String serializeTopTags(List<Object[]> tagRows, int limit) {
        List<Map<String, Object>> tagList = new ArrayList<>();
        int count = Math.min(tagRows.size(), limit);
        for (int i = 0; i < count; i++) {
            Object[] row = tagRows.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("tag", row[0]);
            entry.put("count", row[1]);
            tagList.add(entry);
        }
        return toJson(tagList);
    }

    // 레이더 차트용: {"tagName": name, "count": count} 형식, 전체 태그
    private String serializeTagActivities(List<Object[]> tagRows) {
        List<Map<String, Object>> tagList = new ArrayList<>();
        for (Object[] row : tagRows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("tagName", row[0]);
            entry.put("count", row[1]);
            tagList.add(entry);
        }
        return toJson(tagList);
    }

    // 바 차트용: 요일별 활동 수 (월~일, 없는 요일은 0)
    private String buildDailyActivitiesJson(UUID userId, LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = historyRepository.findDailyActivityCountsByUserAndPeriod(userId, from, to);
        Map<Integer, Long> countsByDow = new LinkedHashMap<>();
        for (Object[] row : rows) {
            int dow = ((Number) row[0]).intValue();
            long cnt = ((Number) row[1]).longValue();
            countsByDow.put(dow, cnt);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dayOfWeek", DAY_NAMES[i]);
            entry.put("count", countsByDow.getOrDefault(i, 0L));
            result.add(entry);
        }
        return toJson(result);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private WeeklyReportResponse toResponse(WeeklyReport report) {
        return WeeklyReportResponse.of(report, buildChartDataResponse(report));
    }

    private ChartDataResponse buildChartDataResponse(WeeklyReport report) {
        if (report.getActivities().isEmpty()) {
            return new ChartDataResponse(List.of(), List.of());
        }
        ReportActivity activity = report.getActivities().get(0);
        List<ChartDataResponse.DailyActivity> daily = parseJson(
                activity.getDailyActivities(),
                new TypeReference<List<ChartDataResponse.DailyActivity>>() {});
        List<ChartDataResponse.TagActivity> tags = parseJson(
                activity.getTagActivities(),
                new TypeReference<List<ChartDataResponse.TagActivity>>() {});
        return new ChartDataResponse(daily, tags);
    }

    private <T> List<T> parseJson(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private LocalDate getWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private LocalDate todaySeoul() {
        return LocalDate.now(ZONE_SEOUL);
    }

    // DP-246: 주간 리포트 조회 시 학습 히스토리 기록
    private void recordWeeklyReportViewed(UUID userId) {
        userRepository.findByIdAndIsActiveTrue(userId).ifPresent(user ->
                historyRepository.save(History.builder()
                        .user(user)
                        .actionType("weekly_report_viewed")
                        .build())
        );
    }
}
