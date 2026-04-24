package com.devpick.domain.trend.controller;

import com.devpick.domain.trend.service.TrendAnalysisService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Hidden
@RestController
@RequestMapping("/internal/trends")
@RequiredArgsConstructor
public class InternalTrendCacheController {

    private final TrendAnalysisService trendAnalysisService;

    @Value("${ai.server.internal-key:}")
    private String cacheEvictKey;

    @DeleteMapping("/cache")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void evictCache(
            @RequestHeader(value = "X-Internal-Key", required = false) String key,
            @RequestParam(defaultValue = "weekly") String unit,
            @RequestParam(defaultValue = "global") String scope,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart) {
        if (cacheEvictKey == null || cacheEvictKey.isBlank()) {
            throw new DevpickException(ErrorCode.ENDPOINT_NOT_FOUND);
        }
        if (key == null || !cacheEvictKey.equals(key)) {
            throw new DevpickException(ErrorCode.FORBIDDEN);
        }
        trendAnalysisService.evictCache(unit, scope, periodStart);
    }
}
