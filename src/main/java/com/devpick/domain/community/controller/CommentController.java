package com.devpick.domain.community.controller;

import com.devpick.domain.community.dto.CommentCreateRequest;
import com.devpick.domain.community.dto.CommentResponse;
import com.devpick.domain.community.service.CommentService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Comment", description = "답변 댓글 작성/삭제")
@RestController
@RequestMapping("/posts/{postId}/answers/{answerId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글 작성", description = "답변에 댓글을 작성합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "작성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "게시글 또는 답변을 찾을 수 없음")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentResponse> createComment(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "게시글 ID (UUID)", required = true) @PathVariable UUID postId,
            @Parameter(description = "답변 ID (UUID)", required = true) @PathVariable UUID answerId,
            @Valid @RequestBody CommentCreateRequest request) {
        return ApiResponse.ok(commentService.createComment(userId, postId, answerId, request));
    }

    @Operation(summary = "댓글 삭제", description = "댓글을 삭제합니다. 작성자만 삭제 가능합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "댓글을 찾을 수 없음")
    })
    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "게시글 ID (UUID)", required = true) @PathVariable UUID postId,
            @Parameter(description = "답변 ID (UUID)", required = true) @PathVariable UUID answerId,
            @Parameter(description = "댓글 ID (UUID)", required = true) @PathVariable UUID commentId) {
        commentService.deleteComment(userId, postId, answerId, commentId);
    }
}
