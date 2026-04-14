package com.devpick.domain.report.service;

import com.devpick.domain.report.client.AiReportClient;
import com.devpick.domain.report.document.ReportInsightDocument;
import com.devpick.domain.report.dto.ChartDataResponse;
import com.devpick.domain.report.dto.ReportInsightResponse;
import com.devpick.domain.report.dto.ReportSummaryResponse;
import com.devpick.domain.report.dto.ShareLinkResponse;
import com.devpick.domain.report.dto.WeeklyReportResponse;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.entity.ReportActivity;
import com.devpick.domain.report.entity.WeeklyReport;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.report.repository.ReportInsightRepository;
import com.devpick.domain.report.repository.WeeklyReportRepository;
import com.devpick.domain.user.entity.User;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final ReportInsightRepository reportInsightRepository;
    private final AiReportClient aiReportClient;
    private final ObjectMapper objectMapper;

    // DP-256: 리포트 목록 조회 (드롭다운용 최소 필드)
    @Transactional(readOnly = true)
    public List<ReportSummaryResponse> getReportList(UUID userId) {
        return weeklyReportRepository.findByUserIdOrderByWeekStartDesc(userId).stream()
                .map(ReportSummaryResponse::of)
                .toList();
    }

    // DP-256: 이번 주 리포트 조회 — 없으면 온디맨드 생성 (OpenAPI·프론트 기대와 일치)
    @Transactional
    public WeeklyReportResponse getCurrentWeekReport(UUID userId) {
        LocalDate weekStart = getWeekStart(todaySeoul());
        WeeklyReportResponse response = generateOrGetReport(userId, weekStart);
        recordWeeklyReportViewed(userId);
        return response;
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
        // history.created_at 은 JVM(보통 UTC) 기준 LocalDateTime — 한국 주 경계를 UTC instant로 맞춤
        ZonedDateTime fromZ = weekStart.atStartOfDay(ZONE_SEOUL);
        ZonedDateTime toZ = weekEnd.atTime(23, 59, 59, 999_999_999).atZone(ZONE_SEOUL);
        LocalDateTime from = LocalDateTime.ofInstant(fromZ.toInstant(), ZoneOffset.UTC);
        LocalDateTime to = LocalDateTime.ofInstant(toZ.toInstant(), ZoneOffset.UTC);

        long contentsRead = historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(
                user.getId(), "content_opened", from, to);
        long questionsCreated = historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(
                user.getId(), "question_created", from, to);
        long scrapsCount = historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(
                user.getId(), "scrapped", from, to);

        List<Object[]> tagRows = historyRepository.findTopTagsByUserAndPeriod(user.getId(), from, to);
        String topTagsJson = serializeTopTags(tagRows, 3);
        String tagActivitiesJson = serializeTagActivities(tagRows);
        String dailyActivitiesJson = buildDailyActivitiesJson(user.getId(), from, to);

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
                .scrapsCount((int) scrapsCount)
                .topTags(topTagsJson)
                .dailyActivities(dailyActivitiesJson)
                .tagActivities(tagActivitiesJson)
                .build();

        report.getActivities().add(activity);
        WeeklyReport saved = weeklyReportRepository.save(report);

        // AI 인사이트 생성 요청 (비동기 — 실패해도 리포트 생성에 영향 없음)
        requestAiInsightAsync(user, saved, weekStart, weekEnd, activity);

        return saved;
    }

    private void requestAiInsightAsync(User user, WeeklyReport report, LocalDate weekStart, LocalDate weekEnd, ReportActivity activity) {
        try {
            List<ChartDataResponse.TagActivity> tagActivities = parseJson(
                    activity.getTagActivities(),
                    new TypeReference<List<ChartDataResponse.TagActivity>>() {});
            List<ChartDataResponse.DailyActivity> dailyActivities = parseJson(
                    activity.getDailyActivities(),
                    new TypeReference<List<ChartDataResponse.DailyActivity>>() {});

            List<Map<String, Object>> topTagsMaps = parseJson(
                    activity.getTopTags(),
                    new TypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> dailyMaps = dailyActivities.stream()
                    .map(d -> Map.<String, Object>of("day_of_week", d.dayOfWeek(), "count", d.count()))
                    .toList();
            List<Map<String, Object>> tagMaps = tagActivities.stream()
                    .map(t -> Map.<String, Object>of("tag_name", t.tagName(), "count", t.count()))
                    .toList();

            AiReportClient.ActivityData activityData = new AiReportClient.ActivityData(
                    activity.getContentsRead(),
                    activity.getQuestionsCreated(),
                    activity.getScrapsCount(),
                    topTagsMaps,
                    dailyMaps,
                    tagMaps,
                    List.of(),
                    List.of(),
                    List.of()
            );

            AiReportClient.InsightRequest insightRequest = new AiReportClient.InsightRequest(
                    report.getId().toString(),
                    user.getId().toString(),
                    weekStart.toString(),
                    weekEnd.toString(),
                    activityData
            );

            aiReportClient.requestInsight(insightRequest);
        } catch (Exception e) {
            log.warn("[WeeklyReport] AI 인사이트 요청 실패 reportId={}: {}", report.getId(), e.getMessage());
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
        ChartDataResponse chartData = buildChartDataResponse(report);
        ReportInsightResponse aiInsight = loadInsight(report.getId());
        return WeeklyReportResponse.of(report, chartData, aiInsight);
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

    private ReportInsightResponse loadInsight(UUID reportId) {
        return reportInsightRepository.findByReportId(reportId.toString())
                .map(doc -> new ReportInsightResponse(doc.getWellDone(), doc.getLacking(), doc.getNextWeek()))
                .orElse(null);
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
