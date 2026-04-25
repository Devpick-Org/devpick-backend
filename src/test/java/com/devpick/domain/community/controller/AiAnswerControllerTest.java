package com.devpick.domain.community.controller;

import com.devpick.domain.community.dto.AiAnswerResponse;
import com.devpick.domain.community.service.AiAnswerService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiAnswerControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AiAnswerService aiAnswerService;

    @InjectMocks
    private AiAnswerController aiAnswerController;

    private UUID postId;
    private AiAnswerResponse aiAnswerResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(aiAnswerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        postId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UUID.randomUUID(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        aiAnswerResponse = new AiAnswerResponse(
                UUID.randomUUID(), postId,
                "AI가 생성한 답변 내용입니다.",
                List.of("핵심 포인트 1", "핵심 포인트 2"),
                List.of("Spring", "Java"),
                0.88,
                false,
                Instant.now()
        );
    }

    @Test
    @DisplayName("POST /posts/{postId}/ai-answer - AI 답변 생성 성공 시 200 반환")
    void generateAiAnswer_success_returns200() throws Exception {
        given(aiAnswerService.generateOrGetAnswer(postId)).willReturn(aiAnswerResponse);

        mockMvc.perform(post("/posts/" + postId + "/ai-answer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("AI가 생성한 답변 내용입니다."))
                .andExpect(jsonPath("$.data.keyPoints[0]").value("핵심 포인트 1"))
                .andExpect(jsonPath("$.data.suggestedTags[0]").value("Spring"))
                .andExpect(jsonPath("$.data.confidence").value(0.88))
                .andExpect(jsonPath("$.data.isAdopted").value(false));
    }

    @Test
    @DisplayName("POST /posts/{postId}/ai-answer - 게시글 없으면 404 반환")
    void generateAiAnswer_postNotFound_returns404() throws Exception {
        given(aiAnswerService.generateOrGetAnswer(postId))
                .willThrow(new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        mockMvc.perform(post("/posts/" + postId + "/ai-answer"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /posts/{postId}/ai-answer - AI 서버 오류 시 500 반환")
    void generateAiAnswer_aiServerError_returns500() throws Exception {
        given(aiAnswerService.generateOrGetAnswer(postId))
                .willThrow(new DevpickException(ErrorCode.AI_SERVER_ERROR));

        mockMvc.perform(post("/posts/" + postId + "/ai-answer"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }
}
