package com.devpick.domain.content.controller;

import com.devpick.domain.content.collector.stackoverflow.StackOverflowCollector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * DP-200 2단계 테스트용 — dev 프로파일에서만 활성화.
 * 실제 서비스 배포 시에는 빈 자체가 등록되지 않음.
 *
 * 사용법:
 *   GET /dev/collect/stackoverflow?tags=java
 *   GET /dev/collect/stackoverflow?tags=spring-boot
 *   GET /dev/collect/stackoverflow?tags=kotlin
 */
@Tag(name = "Dev", description = "[DEV 전용] 수집 트리거 API")
@Profile("dev")
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
public class DevCollectController {

    private final StackOverflowCollector stackOverflowCollector;

    @Operation(
        summary = "[DEV] Stack Overflow 수집 트리거",
        description = "StackOverflowCollector.collect(tags) 를 직접 실행. " +
                      "SO API 호출 → 파싱 → PostgreSQL 저장까지 end-to-end 검증용."
    )
    @GetMapping("/collect/stackoverflow")
    public ResponseEntity<Map<String, Object>> collectStackOverflow(
            @RequestParam(defaultValue = "java") String tags) {

        int saved = stackOverflowCollector.collect(tags);

        return ResponseEntity.ok(Map.of(
                "source", "Stack Overflow",
                "tags", tags,
                "saved", saved
        ));
    }
}
