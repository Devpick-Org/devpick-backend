package com.devpick.domain.community.controller;

import com.devpick.domain.community.dto.AnswerCreateRequest;
import com.devpick.domain.community.dto.AnswerResponse;
import com.devpick.domain.community.dto.AnswerUpdateRequest;
import com.devpick.domain.community.service.AnswerService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AnswerControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AnswerService answerService;

    @InjectMocks
    private AnswerController answerController;

    private UUID userId;
    private UUID postId;
    private UUID answerId;
    private AnswerResponse answerResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(answerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
        postId = UUID.randomUUID();
        answerId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        answerResponse = new AnswerResponse(
                answerId, postId, "Test Answer", false,
                userId, "tester",
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /posts/{postId}/answers - 답변 작성 성공 시 201 반환")
    void createAnswer_success_returns201() throws Exception {
        AnswerCreateRequest request = new AnswerCreateRequest("Test Answer");
        given(answerService.createAnswer(eq(userId), eq(postId), any())).willReturn(answerResponse);

        mockMvc.perform(post("/posts/" + postId + "/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("Test Answer"));
    }

    @Test
    @DisplayName("POST /posts/{postId}/answers - 게시글 없으면 404 반환")
    void createAnswer_postNotFound_returns404() throws Exception {
        AnswerCreateRequest request = new AnswerCreateRequest("content");
        given(answerService.createAnswer(eq(userId), eq(postId), any()))
                .willThrow(new DevpickException(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        mockMvc.perform(post("/posts/" + postId + "/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PUT /posts/{postId}/answers/{answerId} - 수정 성공 시 200 반환")
    void updateAnswer_success_returns200() throws Exception {
        AnswerUpdateRequest request = new AnswerUpdateRequest("Updated Answer");
        AnswerResponse updated = new AnswerResponse(
                answerId, postId, "Updated Answer", false,
                userId, "tester", LocalDateTime.now(), LocalDateTime.now());
        given(answerService.updateAnswer(eq(userId), eq(postId), eq(answerId), any())).willReturn(updated);

        mockMvc.perform(put("/posts/" + postId + "/answers/" + answerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("Updated Answer"));
    }

    @Test
    @DisplayName("PUT /posts/{postId}/answers/{answerId} - 권한 없으면 403 반환")
    void updateAnswer_unauthorized_returns403() throws Exception {
        AnswerUpdateRequest request = new AnswerUpdateRequest("Updated");
        given(answerService.updateAnswer(eq(userId), eq(postId), eq(answerId), any()))
                .willThrow(new DevpickException(ErrorCode.COMMUNITY_UNAUTHORIZED_ANSWER_ACTION));

        mockMvc.perform(put("/posts/" + postId + "/answers/" + answerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /posts/{postId}/answers/{answerId} - 삭제 성공 시 204 반환")
    void deleteAnswer_success_returns204() throws Exception {
        mockMvc.perform(delete("/posts/" + postId + "/answers/" + answerId))
                .andExpect(status().isNoContent());

        verify(answerService).deleteAnswer(userId, postId, answerId);
    }

    @Test
    @DisplayName("POST /posts/{postId}/answers/{answerId}/adopt - 채택 성공 시 200 반환")
    void adoptAnswer_success_returns200() throws Exception {
        AnswerResponse adopted = new AnswerResponse(
                answerId, postId, "Test Answer", true,
                userId, "tester", LocalDateTime.now(), LocalDateTime.now());
        given(answerService.adoptAnswer(userId, postId, answerId)).willReturn(adopted);

        mockMvc.perform(post("/posts/" + postId + "/answers/" + answerId + "/adopt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isAdopted").value(true));
    }

    @Test
    @DisplayName("POST /posts/{postId}/answers/{answerId}/adopt - 이미 채택된 경우 409 반환")
    void adoptAnswer_alreadyAdopted_returns409() throws Exception {
        given(answerService.adoptAnswer(userId, postId, answerId))
                .willThrow(new DevpickException(ErrorCode.COMMUNITY_ALREADY_ADOPTED));

        mockMvc.perform(post("/posts/" + postId + "/answers/" + answerId + "/adopt"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }
}
