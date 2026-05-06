package com.devpick.domain.job.controller;

import com.devpick.domain.job.dto.JobApiModels.JobBookmarkItemResponse;
import com.devpick.domain.job.dto.JobApiModels.JobBookmarkListResponse;
import com.devpick.domain.job.service.JobService;
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
class JobBookmarkControllerTest {

    private MockMvc mockMvc;

    @Mock private JobService jobService;
    @InjectMocks private JobBookmarkController jobBookmarkController;

    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(jobBookmarkController)
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
    @DisplayName("GET /users/me/bookmarks - 정상 조회 시 200 반환")
    void getBookmarks_success_returns200() throws Exception {
        JobBookmarkItemResponse item = new JobBookmarkItemResponse(
                UUID.randomUUID(), "카카오", "https://logo.jpg",
                "백엔드 개발자", "FULL_TIME", "JUNIOR",
                "서울", "2026-06-30", List.of("Java", "Spring"), 75, Instant.now()
        );
        JobBookmarkListResponse response = new JobBookmarkListResponse(List.of(item), 0, 20, 1L, 1);
        given(jobService.getBookmarkedJobs(eq(userId), isNull(), any())).willReturn(response);

        mockMvc.perform(get("/users/me/bookmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bookmarks[0].companyName").value("카카오"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /users/me/bookmarks - 북마크 없을 시 빈 배열 반환")
    void getBookmarks_empty_returns200WithEmptyList() throws Exception {
        JobBookmarkListResponse response = new JobBookmarkListResponse(List.of(), 0, 20, 0L, 0);
        given(jobService.getBookmarkedJobs(any(), any(), any())).willReturn(response);

        mockMvc.perform(get("/users/me/bookmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarks").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /users/me/bookmarks - q 파라미터 전달 시 서비스에 전달됨")
    void getBookmarks_withQuery_passesToService() throws Exception {
        JobBookmarkListResponse response = new JobBookmarkListResponse(List.of(), 0, 20, 0L, 0);
        given(jobService.getBookmarkedJobs(eq(userId), eq("카카오"), any())).willReturn(response);

        mockMvc.perform(get("/users/me/bookmarks").param("q", "카카오"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /users/me/bookmarks - sort=oldest 파라미터 전달 시 200 반환")
    void getBookmarks_withOldestSort_returns200() throws Exception {
        JobBookmarkListResponse response = new JobBookmarkListResponse(List.of(), 0, 20, 0L, 0);
        given(jobService.getBookmarkedJobs(eq(userId), isNull(), any())).willReturn(response);

        mockMvc.perform(get("/users/me/bookmarks").param("sort", "oldest"))
                .andExpect(status().isOk());
    }
}
