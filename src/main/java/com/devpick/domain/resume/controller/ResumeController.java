package com.devpick.domain.resume.controller;

import com.devpick.domain.resume.service.ResumeService;
import com.devpick.global.common.response.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Resume", description = "마스터 이력서 (Epic G)")
@RestController
@RequestMapping("/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @Operation(summary = "마스터 이력서 조회")
    @GetMapping("/master")
    public ApiResponse<JsonNode> getMaster(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(resumeService.getMaster(userId));
    }

    @Operation(summary = "마스터 이력서 저장")
    @PutMapping("/master")
    public ApiResponse<JsonNode> putMaster(
            @AuthenticationPrincipal UUID userId,
            @RequestBody JsonNode body) {
        return ApiResponse.ok(resumeService.upsertMaster(userId, body));
    }
}
