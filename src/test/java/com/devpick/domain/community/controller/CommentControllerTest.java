package com.devpick.domain.community.controller;

import com.devpick.domain.community.dto.CommentCreateRequest;
import com.devpick.domain.community.dto.CommentResponse;
import com.devpick.domain.community.service.CommentService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private CommentService commentService;

    @InjectMocks
    private CommentController commentController;

    private UUID userId;
    private UUID postId;
    private UUID answerId;
    private UUID commentId;
    private CommentResponse commentResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(commentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
        postId = UUID.randomUUID();
        answerId = UUID.randomUUID();
        commentId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        commentResponse = new CommentResponse(
                commentId, answerId, userId, "tester", "Test Comment",
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── POST /posts/{postId}/answers/{answerId}/comments ─────────────────

    @Test
    @DisplayName("POST /posts/{postId}/answers/{answerId}/comments — 성공 시 201 반환")
    void createComment_success_returns201() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("Test Comment");
        given(commentService.createComment(eq(userId), eq(postId), eq(answerId), any(CommentCreateRequest.class)))
                .willReturn(commentResponse);

        mockMvc.perform(post("/posts/{postId}/answers/{answerId}/comments", postId, answerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("Test Comment"))
                .andExpect(jsonPath("$.data.nickname").value("tester"));
    }

    @Test
    @DisplayName("POST — content 비어있으면 400 반환")
    void createComment_blankContent_returns400() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("");

        mockMvc.perform(post("/posts/{postId}/answers/{answerId}/comments", postId, answerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST — 게시글 없으면 404 반환")
    void createComment_postNotFound_returns404() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("Test Comment");
        given(commentService.createComment(any(), any(), any(), any()))
                .willThrow(new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        mockMvc.perform(post("/posts/{postId}/answers/{answerId}/comments", postId, answerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_001"));
    }

    @Test
    @DisplayName("POST — 답변 없으면 404 반환")
    void createComment_answerNotFound_returns404() throws Exception {
        CommentCreateRequest request = new CommentCreateRequest("Test Comment");
        given(commentService.createComment(any(), any(), any(), any()))
                .willThrow(new DevpickException(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));

        mockMvc.perform(post("/posts/{postId}/answers/{answerId}/comments", postId, answerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_002"));
    }

    // ─── DELETE /posts/{postId}/answers/{answerId}/comments/{commentId} ───

    @Test
    @DisplayName("DELETE /posts/{postId}/answers/{answerId}/comments/{commentId} — 성공 시 204 반환")
    void deleteComment_success_returns204() throws Exception {
        doNothing().when(commentService).deleteComment(userId, postId, answerId, commentId);

        mockMvc.perform(delete("/posts/{postId}/answers/{answerId}/comments/{commentId}",
                        postId, answerId, commentId))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE — 댓글 없으면 404 반환")
    void deleteComment_commentNotFound_returns404() throws Exception {
        doThrow(new DevpickException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND))
                .when(commentService).deleteComment(any(), any(), any(), any());

        mockMvc.perform(delete("/posts/{postId}/answers/{answerId}/comments/{commentId}",
                        postId, answerId, commentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_007"));
    }

    @Test
    @DisplayName("DELETE — 작성자 아니면 403 반환")
    void deleteComment_unauthorized_returns403() throws Exception {
        doThrow(new DevpickException(ErrorCode.COMMUNITY_UNAUTHORIZED_COMMENT_ACTION))
                .when(commentService).deleteComment(any(), any(), any(), any());

        mockMvc.perform(delete("/posts/{postId}/answers/{answerId}/comments/{commentId}",
                        postId, answerId, commentId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_008"));
    }
}
