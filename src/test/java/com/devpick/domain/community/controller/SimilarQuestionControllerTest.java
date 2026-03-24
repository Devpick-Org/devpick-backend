package com.devpick.domain.community.controller;

import com.devpick.domain.community.dto.SimilarPostListResponse;
import com.devpick.domain.community.dto.SimilarPostResponse;
import com.devpick.domain.community.service.SimilarQuestionService;
import com.devpick.domain.user.entity.Level;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.devpick.global.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SimilarQuestionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SimilarQuestionService similarQuestionService;

    @InjectMocks
    private SimilarQuestionController similarQuestionController;

    private UUID postId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(similarQuestionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        postId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UUID.randomUUID(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
    }

    @Test
    @DisplayName("GET /posts/{postId}/similar - 유사 질문 조회 성공 시 200 반환")
    void getSimilarQuestions_success_returns200() throws Exception {
        SimilarPostResponse post1 = new SimilarPostResponse(
                UUID.randomUUID(), "Spring Filter vs Interceptor", Level.JUNIOR, 3, LocalDateTime.now());
        SimilarPostListResponse response = new SimilarPostListResponse(List.of(post1));
        given(similarQuestionService.getSimilarPosts(postId)).willReturn(response);

        mockMvc.perform(get("/posts/" + postId + "/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.posts[0].title").value("Spring Filter vs Interceptor"));
    }

    @Test
    @DisplayName("GET /posts/{postId}/similar - 유사 질문 없으면 빈 배열 반환")
    void getSimilarQuestions_empty_returnsEmptyList() throws Exception {
        given(similarQuestionService.getSimilarPosts(postId))
                .willReturn(new SimilarPostListResponse(List.of()));

        mockMvc.perform(get("/posts/" + postId + "/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posts").isEmpty());
    }

    @Test
    @DisplayName("GET /posts/{postId}/similar - 게시글 없으면 404 반환")
    void getSimilarQuestions_postNotFound_returns404() throws Exception {
        given(similarQuestionService.getSimilarPosts(postId))
                .willThrow(new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        mockMvc.perform(get("/posts/" + postId + "/similar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /posts/{postId}/similar - AI 서버 오류 시 500 반환")
    void getSimilarQuestions_aiServerError_returns500() throws Exception {
        given(similarQuestionService.getSimilarPosts(postId))
                .willThrow(new DevpickException(ErrorCode.AI_SERVER_ERROR));

        mockMvc.perform(get("/posts/" + postId + "/similar"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }
}