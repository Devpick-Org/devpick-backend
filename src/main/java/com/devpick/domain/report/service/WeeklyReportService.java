package com.devpick.domain.report.service;

import com.devpick.domain.report.dto.ShareLinkResponse;
import com.devpick.domain.report.dto.WeeklyReportResponse;
import com.devpick.domain.report.entity.ReportActivity;
import com.devpick.domain.report.entity.WeeklyReport;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.report.repository.WeeklyReportRepository;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
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

    private final WeeklyReportRepository weeklyReportRepository;
    private final HistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // DP-256: 이번 주 리포트 조회
    @Transactional(readOnly = true)
    public WeeklyReportResponse getCurrentWeekReport(UUID userId) {
        LocalDate weekStart = getWeekStart(LocalDate.now());
        WeeklyReport report = weeklyReportRepository
                .findWithActivitiesByUser_IdAndWeekStart(userId, weekStart)
                .orElseThrow(() -> new DevpickException(ErrorCode.REPORT_NOT_FOUND));
        return WeeklyReportResponse.of(report);
    }

    // DP-256: 특정 reportId로 리포트 조회
    @Transactional(readOnly = true)
    public WeeklyReportResponse getReportById(UUID userId, UUID reportId) {
        WeeklyReport report = weeklyReportRepository.findWithActivitiesById(reportId)
                .orElseThrow(() -> new DevpickException(ErrorCode.REPORT_NOT_FOUND));
        if (!report.getUser().getId().equals(userId)) {
            throw new DevpickException(ErrorCode.REPORT_FORBIDDEN);
        }
        return WeeklyReportResponse.of(report);
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
        return WeeklyReportResponse.of(report);
    }

    // DP-255: 매주 월요일 00:05에 전 주 리포트 자동 생성
    @Scheduled(cron = "0 5 0 * * MON", zone = "Asia/Seoul")
    @Transactional
    public void generateWeeklyReports() {
        LocalDate lastWeekStart = getWeekStart(LocalDate.now().minusWeeks(1));
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
            return WeeklyReportResponse.of(
                    weeklyReportRepository.findWithActivitiesByUser_IdAndWeekStart(userId, weekStart)
                            .orElseThrow(() -> new DevpickException(ErrorCode.REPORT_NOT_FOUND))
            );
        }
        User user = userRepository.findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new DevpickException(ErrorCode.USER_NOT_FOUND));
        LocalDate weekEnd = weekStart.plusDays(6);
        WeeklyReport report = generateReportForUser(user, weekStart, weekEnd);
        return WeeklyReportResponse.of(report);
    }

    private WeeklyReport generateReportForUser(User user, LocalDate weekStart, LocalDate weekEnd) {
        LocalDateTime from = weekStart.atStartOfDay();
        LocalDateTime to = weekEnd.atTime(LocalTime.MAX);

        long contentsRead = historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(
                user.getId(), "content_opened", from, to);
        long questionsCreated = historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(
                user.getId(), "question_created", from, to);
        long scrapsCount = historyRepository.countByUser_IdAndActionTypeAndCreatedAtBetween(
                user.getId(), "content_saved", from, to);

        String topTagsJson = buildTopTagsJson(user.getId(), from, to);

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
                .build();

        report.getActivities().add(activity);
        return weeklyReportRepository.save(report);
    }

    private String buildTopTagsJson(UUID userId, LocalDateTime from, LocalDateTime to) {
        List<Object[]> tagRows = historyRepository.findTopTagsByUserAndPeriod(userId, from, to);
        List<Map<String, Object>> tagList = new ArrayList<>();
        int limit = Math.min(tagRows.size(), 5);
        for (int i = 0; i < limit; i++) {
            Object[] row = tagRows.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("tag", row[0]);
            entry.put("count", row[1]);
            tagList.add(entry);
        }
        try {
            return objectMapper.writeValueAsString(tagList);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private LocalDate getWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
