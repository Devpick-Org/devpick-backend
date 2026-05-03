package com.devpick.domain.job.controller;

import com.devpick.domain.job.dto.MockInterviewModels.AnswerOutcome;
import com.devpick.domain.job.dto.MockInterviewModels.AnswerRequest;
import com.devpick.domain.job.dto.MockInterviewModels.AvailableModelsResponse;
import com.devpick.domain.job.dto.MockInterviewModels.DeleteManyRequest;
import com.devpick.domain.job.dto.MockInterviewModels.HistoryListResponse;
import com.devpick.domain.job.dto.MockInterviewModels.PassRequest;
import com.devpick.domain.job.dto.MockInterviewModels.SessionDetailResponse;
import com.devpick.domain.job.dto.MockInterviewModels.StartFromJdRequest;
import com.devpick.domain.job.dto.MockInterviewModels.StartFromJobRequest;
import com.devpick.domain.job.service.MockInterviewModelRegistry;
import com.devpick.domain.job.service.MockInterviewService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Mock Interview", description = "채팅형 모의면접 (Epic G)")
@RestController
@RequestMapping("/jobs/mock-interviews")
@RequiredArgsConstructor
public class MockInterviewController {

    private final MockInterviewService mockInterviewService;
    private final MockInterviewModelRegistry modelRegistry;

    @Operation(summary = "선택 가능한 면접용 Bedrock 모델 옵션")
    @GetMapping("/models")
    public ApiResponse<AvailableModelsResponse> models() {
        return ApiResponse.ok(modelRegistry.list());
    }

    @Operation(summary = "내 모의면접 히스토리")
    @GetMapping
    public ApiResponse<HistoryListResponse> list(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(mockInterviewService.listForUser(userId));
    }

    @Operation(summary = "공고 기반 모의면접 시작")
    @PostMapping("/start/job/{jobId}")
    public ApiResponse<SessionDetailResponse> startFromJob(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID jobId,
            @RequestBody(required = false) StartFromJobRequest body) {
        StartFromJobRequest request = body != null ? body : new StartFromJobRequest(null, null, null);
        return ApiResponse.ok(mockInterviewService.startFromJob(userId, jobId, request));
    }

    @Operation(summary = "JD 직접 입력 모의면접 시작")
    @PostMapping("/start")
    public ApiResponse<SessionDetailResponse> startFromJd(
            @AuthenticationPrincipal UUID userId,
            @RequestBody StartFromJdRequest body) {
        return ApiResponse.ok(mockInterviewService.startFromJd(userId, body));
    }

    @Operation(summary = "모의면접 세션 상세/이어하기")
    @GetMapping("/{sessionId}")
    public ApiResponse<SessionDetailResponse> get(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId) {
        return ApiResponse.ok(mockInterviewService.get(userId, sessionId));
    }

    @Operation(summary = "답변 제출 후 평가/꼬리/재답변/다음 결정")
    @PostMapping("/{sessionId}/answer")
    public ApiResponse<AnswerOutcome> answer(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId,
            @RequestBody AnswerRequest body) {
        return ApiResponse.ok(mockInterviewService.submitAnswer(userId, sessionId, body));
    }

    @Operation(summary = "현재 질문 패스")
    @PostMapping("/{sessionId}/pass")
    public ApiResponse<SessionDetailResponse> pass(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId,
            @RequestBody(required = false) PassRequest body) {
        int qNo = body != null ? body.questionNo() : 0;
        return ApiResponse.ok(mockInterviewService.passQuestion(userId, sessionId, qNo));
    }

    @Operation(summary = "저장 후 나가기")
    @PostMapping("/{sessionId}/save-exit")
    public ApiResponse<SessionDetailResponse> saveAndExit(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId) {
        return ApiResponse.ok(mockInterviewService.saveAndExit(userId, sessionId));
    }

    @Operation(summary = "여기서 마치기 — 조기 종료 평가")
    @PostMapping("/{sessionId}/finish-early")
    public ApiResponse<SessionDetailResponse> finishEarly(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId) {
        return ApiResponse.ok(mockInterviewService.finishEarly(userId, sessionId));
    }

    @Operation(summary = "모의면접 단건 삭제")
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId) {
        mockInterviewService.delete(userId, sessionId);
        return ApiResponse.ok();
    }

    @Operation(summary = "모의면접 일괄 삭제")
    @DeleteMapping
    public ApiResponse<Void> deleteMany(
            @AuthenticationPrincipal UUID userId,
            @RequestBody DeleteManyRequest body) {
        mockInterviewService.deleteMany(userId, body == null ? null : body.sessionIds());
        return ApiResponse.ok();
    }
}
