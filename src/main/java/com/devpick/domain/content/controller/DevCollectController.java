package com.devpick.domain.content.controller;

import com.devpick.domain.content.collector.ContentCollector;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * DP-200 2단계 테스트용 — dev 프로파일에서만 활성화.
 * 실제 서비스 배포 시에는 빈 자체가 등록되지 않음.
 *
 * 사용법:
 *   GET /dev/collect/stackoverflow?tags=java
 *   GET /dev/collect/velog?tags=spring-boot
 */
@Tag(name = "Dev", description = "[DEV 전용] 수집 트리거 API")
@Profile("dev")
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
public class DevCollectController {

    private final List<ContentCollector> contentCollectors;

    @Operation(
        summary = "[DEV] 수집 트리거",
        description = "source 이름으로 ContentCollector를 선택하여 collect(tags) 실행. " +
                      "예: stackoverflow, velog"
    )
    @GetMapping("/collect/{source}")
    public ResponseEntity<Map<String, Object>> collect(
            @PathVariable String source,
            @RequestParam(defaultValue = "java") String tags) {

        ContentCollector collector = contentCollectors.stream()
                .filter(c -> c.sourceName().equalsIgnoreCase(source))
                .findFirst()
                .orElseThrow(() -> new DevpickException(ErrorCode.CONTENT_SOURCE_NOT_FOUND));

        int saved = collector.collect(tags);

        return ResponseEntity.ok(Map.of(
                "source", source,
                "tags", tags,
                "saved", saved
        ));
    }
}
