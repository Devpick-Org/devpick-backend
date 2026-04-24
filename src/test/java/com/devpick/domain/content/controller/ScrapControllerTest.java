package com.devpick.domain.content.controller;

import com.devpick.domain.content.dto.ScrapItemResponse;
import com.devpick.domain.content.dto.ScrapListResponse;
import com.devpick.domain.content.service.ScrapService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ScrapControllerTest {

    private MockMvc mockMvc;

    @Mock private ScrapService scrapService;
    @InjectMocks private ScrapController scrapController;

    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(scrapController)
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
    @DisplayName("GET /users/me/scraps - 정상 조회 시 200 반환")
    void getScraps_success_returns200() throws Exception {
        ScrapItemResponse item = new ScrapItemResponse(
                UUID.randomUUID(), UUID.randomUUID(),
                "Spring Boot 완전 정복", "velog",
                "https://thumb.jpg", "핵심 요약", Instant.now()
        );
        ScrapListResponse response = new ScrapListResponse(List.of(item), 0, 10, 1L, 1);
        given(scrapService.getScraps(eq(userId), isNull(), any(), any())).willReturn(response);

        mockMvc.perform(get("/users/me/scraps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("Spring Boot 완전 정복"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /users/me/scraps - 빈 결과 시 200 반환")
    void getScraps_empty_returns200WithEmptyContent() throws Exception {
        ScrapListResponse response = new ScrapListResponse(List.of(), 0, 10, 0L, 0);
        given(scrapService.getScraps(any(), any(), any(), any())).willReturn(response);

        mockMvc.perform(get("/users/me/scraps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /users/me/scraps - q 파라미터 전달 시 서비스에 전달됨")
    void getScraps_withQuery_passesQueryToService() throws Exception {
        ScrapListResponse response = new ScrapListResponse(List.of(), 0, 10, 0L, 0);
        given(scrapService.getScraps(eq(userId), eq("Spring"), any(), any())).willReturn(response);

        mockMvc.perform(get("/users/me/scraps").param("q", "Spring"))
                .andExpect(status().isOk());
    }
}
