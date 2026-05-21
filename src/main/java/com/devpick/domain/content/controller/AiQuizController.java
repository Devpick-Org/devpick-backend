package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.AiQuizResponse;
import com.devpick.domain.content.dto.QuizSubmitRequest;
import com.devpick.domain.content.dto.QuizSubmitResponse;
import com.devpick.domain.content.service.AiQuizService;
import com.devpick.domain.user.service.UserService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "AI Quiz", description = "콘텐츠 기반 AI 퀴즈 조회 및 결과 제출")
@RestController
@RequestMapping("/contents/{contentId}/quiz")
@RequiredArgsConstructor
public class AiQuizController {

    private final AiQuizService aiQuizService;
    private final UserService userService;

    @Operation(summary = "AI 퀴즈 조회", description = "레벨별 AI 퀴즈를 조회합니다. Redis → MongoDB → FastAPI 순으로 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "콘텐츠를 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "AI 서버 오류")
    })
    @GetMapping
    public ApiResponse<AiQuizResponse> getQuiz(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "콘텐츠 ID (UUID)", required = true) @PathVariable UUID contentId,
            @Parameter(description = "퀴즈 레벨. 생략 시 로그인 사용자는 프로필 경력 수준, 비로그인은 JUNIOR", example = "MIDDLE")
            @RequestParam(required = false) String level) {
        String resolved = userService.resolvePreferredAiLevel(userId, level);
        userService.checkAiLevelAccess(userId, resolved);
        return ApiResponse.ok(aiQuizService.getQuiz(userId, contentId, resolved));
    }

    @Operation(summary = "AI 퀴즈 결과 제출", description = "퀴즈 결과를 제출합니다. 통과 시 히스토리가 기록되고 포인트가 적립됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제출 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "콘텐츠를 찾을 수 없음")
    })
    @PostMapping("/submit")
    public ApiResponse<QuizSubmitResponse> submitQuiz(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "콘텐츠 ID (UUID)", required = true) @PathVariable UUID contentId,
            @RequestBody QuizSubmitRequest request) {
        return ApiResponse.ok(aiQuizService.submitQuiz(userId, contentId, request));
    }
}
