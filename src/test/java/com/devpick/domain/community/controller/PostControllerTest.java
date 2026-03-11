package com.devpick.domain.community.controller;

import com.devpick.domain.community.dto.PostCreateRequest;
import com.devpick.domain.community.dto.PostDetailResponse;
import com.devpick.domain.community.dto.PostListResponse;
import com.devpick.domain.community.dto.PostSummaryResponse;
import com.devpick.domain.community.dto.PostUpdateRequest;
import com.devpick.domain.community.service.PostService;
import com.devpick.domain.user.entity.Level;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private PostService postService;

    @InjectMocks
    private PostController postController;

    private UUID userId;
    private UUID postId;
    private PostDetailResponse detailResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(postController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
        postId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        detailResponse = new PostDetailResponse(
                postId, "Test Post", "Test Content", Level.JUNIOR,
                userId, "tester", 2L,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /posts - 게시글 작성 성공 시 201과 응답 반환")
    void createPost_success_returns201() throws Exception {
        PostCreateRequest request = new PostCreateRequest("Test Post", "Test Content", Level.JUNIOR);
        given(postService.createPost(eq(userId), any())).willReturn(detailResponse);

        mockMvc.perform(post("/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Test Post"))
                .andExpect(jsonPath("$.data.answerCount").value(2));
    }

    @Test
    @DisplayName("POST /posts - 유효성 검사 실패 시 400 반환")
    void createPost_validationFails_returns400() throws Exception {
        PostCreateRequest request = new PostCreateRequest("", "content", Level.JUNIOR);

        mockMvc.perform(post("/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /posts - 목록 조회 성공 시 200과 목록 반환")
    void getPosts_success_returns200() throws Exception {
        PostSummaryResponse summary = new PostSummaryResponse(
                postId, "Test Post", Level.JUNIOR, "tester", LocalDateTime.now());
        PostListResponse listResponse = new PostListResponse(List.of(summary), 0, 20, 1L, 1);
        given(postService.getPosts(any())).willReturn(listResponse);

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.posts[0].title").value("Test Post"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /posts/{postId} - 상세 조회 성공 시 200과 상세 반환")
    void getPostDetail_success_returns200() throws Exception {
        given(postService.getPostDetail(postId)).willReturn(detailResponse);

        mockMvc.perform(get("/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("Test Content"))
                .andExpect(jsonPath("$.data.authorNickname").value("tester"));
    }

    @Test
    @DisplayName("GET /posts/{postId} - 게시글 없으면 404 반환")
    void getPostDetail_notFound_returns404() throws Exception {
        given(postService.getPostDetail(postId))
                .willThrow(new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        mockMvc.perform(get("/posts/" + postId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PUT /posts/{postId} - 수정 성공 시 200 반환")
    void updatePost_success_returns200() throws Exception {
        PostUpdateRequest request = new PostUpdateRequest("Updated", "Updated Content", Level.SENIOR);
        PostDetailResponse updated = new PostDetailResponse(
                postId, "Updated", "Updated Content", Level.SENIOR,
                userId, "tester", 0L, LocalDateTime.now(), LocalDateTime.now());
        given(postService.updatePost(eq(userId), eq(postId), any())).willReturn(updated);

        mockMvc.perform(put("/posts/" + postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Updated"));
    }

    @Test
    @DisplayName("PUT /posts/{postId} - 권한 없으면 403 반환")
    void updatePost_unauthorized_returns403() throws Exception {
        PostUpdateRequest request = new PostUpdateRequest("Updated", "Content", Level.JUNIOR);
        given(postService.updatePost(eq(userId), eq(postId), any()))
                .willThrow(new DevpickException(ErrorCode.COMMUNITY_UNAUTHORIZED_POST_ACTION));

        mockMvc.perform(put("/posts/" + postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /posts/{postId} - 삭제 성공 시 204 반환")
    void deletePost_success_returns204() throws Exception {
        mockMvc.perform(delete("/posts/" + postId))
                .andExpect(status().isNoContent());

        verify(postService).deletePost(userId, postId);
    }

    @Test
    @DisplayName("DELETE /posts/{postId} - 권한 없으면 403 반환")
    void deletePost_unauthorized_returns403() throws Exception {
        doThrow(new DevpickException(ErrorCode.COMMUNITY_UNAUTHORIZED_POST_ACTION))
                .when(postService).deletePost(userId, postId);

        mockMvc.perform(delete("/posts/" + postId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}
