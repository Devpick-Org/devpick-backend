package com.devpick.domain.job.controller;

import com.devpick.domain.job.dto.JobApiModels.JobDetailResponse;
import com.devpick.domain.job.dto.JobApiModels.JobIngestRequest;
import com.devpick.domain.job.entity.JobPostingStatus;
import com.devpick.domain.job.repository.JobPostingRepository;
import com.devpick.domain.job.service.JobIngestService;
import com.devpick.domain.job.service.JobService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Hidden
@RestController
@RequestMapping({"/internal/jobs", "/v1/internal/jobs"})
@RequiredArgsConstructor
public class InternalJobController {

    private final JobIngestService jobIngestService;
    private final JobPostingRepository jobPostingRepository;
    private final JobService jobService;

    @Value("${ai.server.internal-key:}")
    private String internalKey;

    /**
     * 운영 점검·크롤 검증: 공개 GET /jobs/{id} 는 로그인 필요 — 동일 페이로드를 내부 키로 조회.
     */
    @GetMapping("/{jobId}/detail")
    public ApiResponse<JobDetailResponse> detailForOps(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @PathVariable UUID jobId) {
        verifyKey(key);
        return ApiResponse.ok(jobService.getJobDetailForInternalOps(jobId));
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Map<String, String>> ingest(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @RequestBody JobIngestRequest body) {
        verifyKey(key);
        var saved = jobIngestService.ingest(body);
        Map<String, String> out = new LinkedHashMap<>();
        out.put("id", saved.getId().toString());
        out.put("status", "ok");
        return ApiResponse.ok(out);
    }

    @PostMapping("/expire")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public ApiResponse<Map<String, Object>> expire(
            @RequestHeader(value = "X-Internal-Key", required = false) String key) {
        verifyKey(key);
        int n = jobPostingRepository.expireActiveBefore(
                JobPostingStatus.EXPIRED, JobPostingStatus.ACTIVE, LocalDate.now());
        return ApiResponse.ok(Map.of("expiredRows", n, "status", "ok"));
    }

    private void verifyKey(String key) {
        if (internalKey == null || internalKey.isBlank()) {
            throw new DevpickException(ErrorCode.ENDPOINT_NOT_FOUND);
        }
        if (key == null || !internalKey.equals(key)) {
            throw new DevpickException(ErrorCode.FORBIDDEN);
        }
    }
}
