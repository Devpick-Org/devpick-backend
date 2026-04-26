package com.devpick.domain.report.controller;

import com.devpick.domain.report.service.WeeklyReportService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 운영/배치용: 스케줄과 동일하게 {@link WeeklyReportService#generateWeeklyReports()}를 수동 실행합니다.
 * {@code WEEKLY_REPORT_TRIGGER_KEY}가 비어 있으면 엔드포인트를 노출하지 않습니다(404).
 */
@Hidden
@RestController
@RequestMapping({"/internal/reports", "/v1/internal/reports"})
@RequiredArgsConstructor
public class InternalWeeklyReportOpsController {

    private final WeeklyReportService weeklyReportService;

    @Value("${app.weekly-report.trigger-key:}")
    private String triggerKey;

    @PostMapping("/weekly/run-batch")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Map<String, String>> runWeeklyBatch(
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {
        if (triggerKey == null || triggerKey.isBlank()) {
            throw new DevpickException(ErrorCode.ENDPOINT_NOT_FOUND);
        }
        if (key == null || !triggerKey.equals(key)) {
            throw new DevpickException(ErrorCode.FORBIDDEN);
        }
        weeklyReportService.generateWeeklyReports();
        return ApiResponse.ok(Map.of("status", "ok"));
    }

    /**
     * 히스토리·가입일 기준으로 지난 주까지 모든 주차에 대해 없는 리포트를 한 번에 생성합니다.
     */
    @PostMapping("/weekly/backfill-from-history")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Map<String, Object>> backfillFromHistory(
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {
        if (triggerKey == null || triggerKey.isBlank()) {
            throw new DevpickException(ErrorCode.ENDPOINT_NOT_FOUND);
        }
        if (key == null || !triggerKey.equals(key)) {
            throw new DevpickException(ErrorCode.FORBIDDEN);
        }
        Map<String, Integer> stats = weeklyReportService.backfillWeeklyReportsFromHistory();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("created", stats.get("created"));
        body.put("usersProcessed", stats.get("usersProcessed"));
        return ApiResponse.ok(body);
    }
}
