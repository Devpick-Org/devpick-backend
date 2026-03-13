package com.devpick.domain.content.controller;

import com.devpick.domain.content.collector.NormalizedContentDto;
import com.devpick.domain.content.dto.IngestResultResponse;
import com.devpick.domain.content.service.InternalContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 레포(FastAPI) 전용 내부 API 컨트롤러.
 * 외부 노출 없음 — Nginx에서 /internal/ 경로 자단 예정 (인증 방식 맰결, 홍근 확인 필요).
 * DP-289
 */
@Tag(name = "Internal", description = "AI 레포 전용 내부 API")
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalContentController {

    private final InternalContentService internalContentService;

    @Operation(summary = "AI 콘텐츠 일괄 수신", description = "AI 레포가 수집/정규화한 콘텐츠를 배치로 전달하면 PostgreSQL에 저장한다.")
    @PostMapping("/contents")
    public ResponseEntity<IngestResultResponse> ingest(
            @RequestBody List<NormalizedContentDto> items) {
        return ResponseEntity.ok(internalContentService.ingest(items));
    }
}
