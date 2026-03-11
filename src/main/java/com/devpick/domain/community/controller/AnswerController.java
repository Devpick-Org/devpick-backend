package com.devpick.domain.community.controller;

import com.devpick.domain.community.dto.AnswerCreateRequest;
import com.devpick.domain.community.dto.AnswerResponse;
import com.devpick.domain.community.dto.AnswerUpdateRequest;
import com.devpick.domain.community.service.AnswerService;
import com.devpick.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Answer", description = "게시글 답변 CRUD 및 채택")
@RestController
@RequestMapping("/posts/{postId}/answers")
@RequiredArgsConstructor
public class AnswerController {

    private final AnswerService answerService;

    @Operation(summary = "답변 작성", description = "게시글에 답변을 작성합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "작성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게시글을 찾을 수 없음")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AnswerResponse> createAnswer(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "게시글 ID (UUID)", required = true) @PathVariable UUID postId,
            @Valid @RequestBody AnswerCreateRequest request) {
        return ApiResponse.ok(answerService.createAnswer(userId, postId, request));
    }

    @Operation(summary = "답변 수정", description = "답변을 수정합니다. 작성자만 수정 가능합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "답변을 찾을 수 없음")
    })
    @PutMapping("/{answerId}")
    public ApiResponse<AnswerResponse> updateAnswer(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "게시글 ID (UUID)", required = true) @PathVariable UUID postId,
            @Parameter(description = "답변 ID (UUID)", required = true) @PathVariable UUID answerId,
            @Valid @RequestBody AnswerUpdateRequest request) {
        return ApiResponse.ok(answerService.updateAnswer(userId, postId, answerId, request));
    }

    @Operation(summary = "답변 삭제", description = "답변을 삭제합니다. 작성자만 삭제 가능합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "답변을 찾을 수 없음")
    })
    @DeleteMapping("/{answerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAnswer(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "게시글 ID (UUID)", required = true) @PathVariable UUID postId,
            @Parameter(description = "답변 ID (UUID)", required = true) @PathVariable UUID answerId) {
        answerService.deleteAnswer(userId, postId, answerId);
    }

    @Operation(summary = "답변 채택", description = "답변을 채택합니다. 게시글 작성자만 채택 가능하며, 게시글당 1개만 채택 가능합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "채택 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "답변을 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 채택된 답변 있음")
    })
    @PostMapping("/{answerId}/adopt")
    public ApiResponse<AnswerResponse> adoptAnswer(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "게시글 ID (UUID)", required = true) @PathVariable UUID postId,
            @Parameter(description = "답변 ID (UUID)", required = true) @PathVariable UUID answerId) {
        return ApiResponse.ok(answerService.adoptAnswer(userId, postId, answerId));
    }
}
