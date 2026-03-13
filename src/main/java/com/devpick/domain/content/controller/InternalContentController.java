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
 * AI 레포 내부 전용 콘텐츠 수신 콘트롤러.
 * /internal/** 경로는 Nginx 네트워크 격리로 외부 노cd9c 차단 예정.
 * DP-289
 */
@Tag(name = "Internal", description = "AI 레포 내부 전용 API (외부 노드 불가)")
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalContentController {

    private final InternalContentService internalContentService;

    @Operation(
            summary = "AI 수집 콘텐츠 일괄 저장",
            description = "AI 레포(FastAPI)가 수집/정규화한 콘텐츠 리스트를 백엔드로 전달하는 내부 API. Nginx 네트워크 격리 적용."
    )
    @PostMapping("/contents")
    public ResponseEntity<IngestResultResponse> ingest(
            @RequestBody List<NormalizedContentDto> items
    ) {
        return ResponseEntity.ok(internalContentService.ingest(items));
    }
}
