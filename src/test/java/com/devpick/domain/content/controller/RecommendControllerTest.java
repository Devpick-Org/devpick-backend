package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.ContentSummaryResponse;
import com.devpick.domain.content.dto.RecommendContentsResponse;
import com.devpick.domain.content.service.RecommendService;
import com.devpick.global.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecommendControllerTest {

    private MockMvc mockMvc;

    @Mock private RecommendService recommendService;
    @InjectMocks private RecommendController recommendController;

    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(recommendController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /recommend/contents - 개인화 성공 시 200, isPersonalized=true, message=null")
    void getRecommendContents_personalized_returns200() throws Exception {
        ContentSummaryResponse summary = new ContentSummaryResponse(
                UUID.randomUUID(), "Spring Boot 추천글", null, "작성자", "Velog",
                "미리보기", null, null, null, "https://velog.io/@test/spring",
                List.of("Spring"), Instant.now(), false, false,
                null, null, null, null);
        RecommendContentsResponse response = new RecommendContentsResponse(
                List.of(summary), true, null);
        given(recommendService.getRecommendContents(eq(userId))).willReturn(response);

        mockMvc.perform(get("/recommend/contents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isPersonalized").value(true))
                .andExpect(jsonPath("$.data.message").doesNotExist())
                .andExpect(jsonPath("$.data.contents[0].title").value("Spring Boot 추천글"));
    }

    @Test
    @DisplayName("GET /recommend/contents - fallback 시 isPersonalized=false, message 포함")
    void getRecommendContents_fallback_returnsMessage() throws Exception {
        RecommendContentsResponse response = new RecommendContentsResponse(
                List.of(), false, "아직 추천할 글이 부족해요. 더 많은 글을 읽어보세요!");
        given(recommendService.getRecommendContents(eq(userId))).willReturn(response);

        mockMvc.perform(get("/recommend/contents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isPersonalized").value(false))
                .andExpect(jsonPath("$.data.message").value("아직 추천할 글이 부족해요. 더 많은 글을 읽어보세요!"));
    }

    @Test
    @DisplayName("GET /recommend/contents - 빈 결과도 200 반환")
    void getRecommendContents_emptyContents_returns200() throws Exception {
        RecommendContentsResponse response = new RecommendContentsResponse(List.of(), true, null);
        given(recommendService.getRecommendContents(eq(userId))).willReturn(response);

        mockMvc.perform(get("/recommend/contents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contents").isEmpty());
    }
}
