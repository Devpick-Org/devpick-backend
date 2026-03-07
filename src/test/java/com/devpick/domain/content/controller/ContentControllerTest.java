package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.ContentDetailResponse;
import com.devpick.domain.content.dto.ContentListResponse;
import com.devpick.domain.content.dto.ContentSummaryResponse;
import com.devpick.domain.content.service.ContentService;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContentService contentService;

    @InjectMocks
    private ContentController contentController;

    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(contentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /contents - 피드 조회 성공 시 200과 콘텐츠 목록 반환")
    void getFeed_success() throws Exception {
        ContentSummaryResponse summary = new ContentSummaryResponse(
                UUID.randomUUID(), "Spring Boot 가이드", "홍근",
                "입문 가이드", "https://velog.io/@test/spring",
                List.of("Spring"), LocalDateTime.now(), false, false);
        ContentListResponse response = new ContentListResponse(List.of(summary), 0, 20, 1L, 1);
        given(contentService.getFeed(eq(userId), any())).willReturn(response);

        mockMvc.perform(get("/contents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contents[0].title").value("Spring Boot 가이드"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /contents/{contentId} - 상세 조회 성공 시 200과 상세 반환")
    void getDetail_success() throws Exception {
        UUID contentId = UUID.randomUUID();
        ContentDetailResponse response = new ContentDetailResponse(
                contentId, "Spring Boot 가이드", "홍근",
                "입문 가이드", "https://velog.io/@test/spring",
                null, false, null, LocalDateTime.now(),
                List.of("Spring"), false, false, "Velog");
        given(contentService.getDetail(userId, contentId)).willReturn(response);

        mockMvc.perform(get("/contents/" + contentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Spring Boot 가이드"))
                .andExpect(jsonPath("$.data.sourceName").value("Velog"));
    }

    @Test
    @DisplayName("GET /contents/{contentId} - 콘텐츠 없으면 404 반환")
    void getDetail_notFound_returns404() throws Exception {
        UUID contentId = UUID.randomUUID();
        given(contentService.getDetail(userId, contentId))
                .willThrow(new DevpickException(ErrorCode.CONTENT_NOT_FOUND));

        mockMvc.perform(get("/contents/" + contentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
