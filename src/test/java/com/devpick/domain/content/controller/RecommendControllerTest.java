package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.BookItem;
import com.devpick.domain.content.dto.BookRecommendResponse;
import com.devpick.domain.content.dto.ContentSummaryResponse;
import com.devpick.domain.content.dto.RecommendContentsResponse;
import com.devpick.domain.content.dto.YoutubeRecommendItem;
import com.devpick.domain.content.dto.YoutubeRecommendResponse;
import com.devpick.domain.content.service.BookRecommendService;
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
    @Mock private BookRecommendService bookRecommendService;
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

    @Test
    @DisplayName("GET /recommend/youtube - 개인화 성공 시 200, isPersonalized=true, videoId 포함")
    void getRecommendYoutube_personalized_returns200() throws Exception {
        YoutubeRecommendItem item = new YoutubeRecommendItem(
                UUID.randomUUID(), "Spring 유튜브 강의", null,
                "abc123", "테스트채널", "PT15M",
                "https://img.youtube.com/thumb.jpg",
                List.of("Spring"), Instant.now(), false, false);
        YoutubeRecommendResponse response = new YoutubeRecommendResponse(List.of(item), true, null);
        given(recommendService.getRecommendYoutube(eq(userId))).willReturn(response);

        mockMvc.perform(get("/recommend/youtube"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isPersonalized").value(true))
                .andExpect(jsonPath("$.data.videos[0].title").value("Spring 유튜브 강의"))
                .andExpect(jsonPath("$.data.videos[0].videoId").value("abc123"))
                .andExpect(jsonPath("$.data.videos[0].channelName").value("테스트채널"));
    }

    @Test
    @DisplayName("GET /recommend/youtube - fallback 시 isPersonalized=false, message 포함")
    void getRecommendYoutube_fallback_returnsMessage() throws Exception {
        YoutubeRecommendResponse response = new YoutubeRecommendResponse(
                List.of(), false, "아직 추천할 글이 부족해요. 더 많은 글을 읽어보세요!");
        given(recommendService.getRecommendYoutube(eq(userId))).willReturn(response);

        mockMvc.perform(get("/recommend/youtube"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPersonalized").value(false))
                .andExpect(jsonPath("$.data.message").value("아직 추천할 글이 부족해요. 더 많은 글을 읽어보세요!"));
    }

    @Test
    @DisplayName("GET /recommend/youtube - 빈 결과도 200 반환")
    void getRecommendYoutube_emptyVideos_returns200() throws Exception {
        YoutubeRecommendResponse response = new YoutubeRecommendResponse(List.of(), true, null);
        given(recommendService.getRecommendYoutube(eq(userId))).willReturn(response);

        mockMvc.perform(get("/recommend/youtube"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videos").isEmpty());
    }

    @Test
    @DisplayName("GET /recommend/books - 개인화 성공 시 200, isPersonalized=true")
    void getRecommendBooks_personalized_returns200() throws Exception {
        BookItem book = new BookItem("Spring Boot 완벽 가이드", List.of("홍근"),
                "위키북스", "https://thumb.jpg", "https://url", "Spring Boot 소개", 30000, 27000);
        BookRecommendResponse response = new BookRecommendResponse(List.of(book), true, null);
        given(bookRecommendService.getRecommendBooks(eq(userId))).willReturn(response);

        mockMvc.perform(get("/recommend/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isPersonalized").value(true))
                .andExpect(jsonPath("$.data.books[0].title").value("Spring Boot 완벽 가이드"));
    }

    @Test
    @DisplayName("GET /recommend/books - 태그 없을 때 isPersonalized=false, message 포함")
    void getRecommendBooks_notEnoughTags_returnsMessage() throws Exception {
        BookRecommendResponse response = new BookRecommendResponse(
                List.of(), false, "관심 태그를 설정하거나 글을 더 읽으면 추천 서적이 나타나요");
        given(bookRecommendService.getRecommendBooks(eq(userId))).willReturn(response);

        mockMvc.perform(get("/recommend/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPersonalized").value(false))
                .andExpect(jsonPath("$.data.message")
                        .value("관심 태그를 설정하거나 글을 더 읽으면 추천 서적이 나타나요"));
    }
}
