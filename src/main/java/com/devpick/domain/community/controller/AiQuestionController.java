package com.devpick.domain.community.controller;

import com.devpick.domain.community.dto.QuestionRefineRequest;
import com.devpick.domain.community.dto.QuestionRefineResponse;
import com.devpick.domain.community.service.AiQuestionService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Question", description = "AI 질문 개선")
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class AiQuestionController {

    private final AiQuestionService aiQuestionService;

    @Operation(summary = "AI 질문 개선", description = "작성 중인 질문을 AI가 개선합니다. 결과는 DB에 저장되지 않습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "개선 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 검사 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "AI 서버 오류")
    })
    @PostMapping("/refine")
    public ApiResponse<QuestionRefineResponse> refine(@Valid @RequestBody QuestionRefineRequest request) {
        return ApiResponse.ok(aiQuestionService.refine(request));
    }
}
