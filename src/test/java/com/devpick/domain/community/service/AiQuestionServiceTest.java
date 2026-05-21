package com.devpick.domain.community.service;

import com.devpick.domain.community.client.AiQuestionClient;
import com.devpick.domain.community.dto.QuestionRefineRequest;
import com.devpick.domain.community.dto.QuestionRefineResponse;
import com.devpick.domain.community.entity.AiQuestion;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.repository.AiQuestionRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.subscription.service.PlanLimitService;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiQuestionServiceTest {

    @InjectMocks
    private AiQuestionService aiQuestionService;

    @Mock
    private AiQuestionClient aiQuestionClient;
    @Mock
    private AiQuestionRepository aiQuestionRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlanLimitService planLimitService;

    private UUID postId;
    private Post post;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        post = Post.builder()
                .title("Spring이란?")
                .content("Spring Framework에 대해 알고 싶어요.")
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(post, "id", postId);
    }

    @Test
    @DisplayName("refine — postId 없이 호출하면 DB 저장 없이 결과 반환")
    void refine_withoutPostId_returnsResponseWithoutSaving() {
        QuestionRefineRequest request = new QuestionRefineRequest(
                "Spring이란?", "Spring Framework에 대해 알고 싶어요.", Level.JUNIOR, null);
        QuestionRefineResponse expected = new QuestionRefineResponse(
                "Spring Framework 핵심 개념이란?",
                "Spring Framework의 IoC, DI, AOP에 대해 설명해주세요.",
                List.of("IoC/DI 개념을 명시하면 더 좋은 답변을 받을 수 있어요"));
        given(aiQuestionClient.refine(request)).willReturn(expected);

        QuestionRefineResponse response = aiQuestionService.refine(null, request);

        assertThat(response.refinedTitle()).isEqualTo("Spring Framework 핵심 개념이란?");
        assertThat(response.suggestions()).hasSize(1);
        verify(postRepository, never()).findById(any());
        verify(aiQuestionRepository, never()).save(any());
    }

    @Test
    @DisplayName("refine — postId 있고 Post가 존재하면 AiQuestion을 저장한다")
    void refine_withPostId_savesAiQuestion() throws Exception {
        QuestionRefineRequest request = new QuestionRefineRequest(
                "Spring이란?", "Spring Framework에 대해 알고 싶어요.", Level.JUNIOR, postId);
        QuestionRefineResponse expected = new QuestionRefineResponse(
                "Spring Framework 핵심 개념이란?",
                "IoC에 대해 설명해주세요.",
                List.of("IoC 태그 추가 권장"));
        given(aiQuestionClient.refine(request)).willReturn(expected);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(aiQuestionRepository.findByPost_Id(postId)).willReturn(Optional.empty());
        given(objectMapper.writeValueAsString(any())).willReturn("[\"IoC 태그 추가 권장\"]");

        QuestionRefineResponse response = aiQuestionService.refine(null, request);

        assertThat(response.refinedTitle()).isEqualTo("Spring Framework 핵심 개념이란?");
        verify(aiQuestionRepository).save(any(AiQuestion.class));
    }

    @Test
    @DisplayName("refine — postId 있지만 Post가 없으면 저장하지 않고 결과 반환")
    void refine_withPostIdButNoPost_returnsResponseWithoutSaving() {
        QuestionRefineRequest request = new QuestionRefineRequest(
                "Spring이란?", "내용입니다.", Level.JUNIOR, postId);
        QuestionRefineResponse expected = new QuestionRefineResponse(
                "refined", "refined content", List.of());
        given(aiQuestionClient.refine(request)).willReturn(expected);
        given(postRepository.findById(postId)).willReturn(Optional.empty());

        QuestionRefineResponse response = aiQuestionService.refine(null, request);

        assertThat(response.refinedTitle()).isEqualTo("refined");
        verify(aiQuestionRepository, never()).save(any());
    }

    @Test
    @DisplayName("refine — postId 있고 이미 AiQuestion이 있으면 save 스킵")
    void refine_whenAiQuestionAlreadyExists_skipsSave() throws Exception {
        QuestionRefineRequest request = new QuestionRefineRequest(
                "Spring이란?", "내용.", Level.JUNIOR, postId);
        QuestionRefineResponse expected = new QuestionRefineResponse("t", "c", List.of());
        AiQuestion existing = AiQuestion.builder()
                .post(post)
                .originalTitle("old")
                .refinedTitle("old")
                .refinedContent("old")
                .build();

        given(aiQuestionClient.refine(request)).willReturn(expected);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(aiQuestionRepository.findByPost_Id(postId)).willReturn(Optional.of(existing));
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        aiQuestionService.refine(null, request);

        verify(aiQuestionRepository, times(1)).findByPost_Id(postId);
        verify(aiQuestionRepository, never()).save(any());
    }

    @Test
    @DisplayName("refine — suggestions 직렬화 실패해도 AiQuestion 저장은 시도한다")
    void refine_suggestionsSerializeFails_stillSaves() throws Exception {
        QuestionRefineRequest request = new QuestionRefineRequest(
                "t", "c", Level.JUNIOR, postId);
        QuestionRefineResponse expected = new QuestionRefineResponse("rt", "rc", List.of("s"));
        given(aiQuestionClient.refine(request)).willReturn(expected);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(aiQuestionRepository.findByPost_Id(postId)).willReturn(Optional.empty());
        given(objectMapper.writeValueAsString(any())).willThrow(new JsonProcessingException("fail") {});

        aiQuestionService.refine(null, request);

        verify(aiQuestionRepository).save(any(AiQuestion.class));
    }

    @Test
    @DisplayName("refine — AI 서버 오류 시 AI_SERVER_ERROR 예외")
    void refine_aiServerError_throwsException() {
        QuestionRefineRequest request = new QuestionRefineRequest("title", "content", Level.JUNIOR, null);
        given(aiQuestionClient.refine(request))
                .willThrow(new DevpickException(ErrorCode.AI_SERVER_ERROR));

        assertThatThrownBy(() -> aiQuestionService.refine(null, request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_SERVER_ERROR));
    }
}
