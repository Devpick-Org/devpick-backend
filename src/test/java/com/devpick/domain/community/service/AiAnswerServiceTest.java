package com.devpick.domain.community.service;

import com.devpick.domain.community.client.AiAnswerClient;
import com.devpick.domain.community.dto.AiAnswerResponse;
import com.devpick.domain.community.entity.AiAnswer;
import com.devpick.domain.community.entity.AiQuestion;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.entity.PostType;
import com.devpick.domain.community.repository.AiAnswerRepository;
import com.devpick.domain.community.repository.AiQuestionRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.subscription.service.PlanLimitService;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.global.common.exception.DevpickException;
import com.devpick.global.common.exception.ErrorCode;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiAnswerServiceTest {

    @InjectMocks
    private AiAnswerService aiAnswerService;

    @Mock
    private AiAnswerRepository aiAnswerRepository;
    @Mock
    private AiQuestionRepository aiQuestionRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private AiAnswerClient aiAnswerClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlanLimitService planLimitService;

    private UUID postId;
    private Post post;
    private AiAnswerClient.AiAnswerFastApiResponse fakeAiResponse;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        post = Post.builder()
                .postType(PostType.TECH)
                .title("Spring 질문")
                .content("내용입니다")
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(post, "id", postId);

        fakeAiResponse = new AiAnswerClient.AiAnswerFastApiResponse(
                "AI가 생성한 답변",
                List.of("핵심 포인트 1", "핵심 포인트 2"),
                List.of("Spring", "Java"),
                0.88
        );
    }

    @Test
    @DisplayName("게시글이 없으면 COMMUNITY_POST_NOT_FOUND 예외가 발생한다")
    void generateOrGetAnswer_postNotFound_throwsException() {
        given(postRepository.findById(postId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiAnswerService.generateOrGetAnswer(null, postId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND));

        verify(aiAnswerRepository, never()).save(any());
        verify(aiAnswerClient, never()).generateAnswer(any(), any());
    }

    @Test
    @DisplayName("AI 답변이 이미 존재하면 기존 답변을 반환하고 FastAPI를 호출하지 않는다")
    void generateOrGetAnswer_existingAnswer_returnsExisting() {
        AiAnswer existing = AiAnswer.builder()
                .post(post)
                .content("기존 AI 답변")
                .keyPoints(List.of("포인트 1"))
                .suggestedTags(List.of("Java"))
                .confidence(0.9)
                .build();
        UUID answerId = UUID.randomUUID();
        ReflectionTestUtils.setField(existing, "id", answerId);

        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(aiAnswerRepository.findByPost_Id(postId)).willReturn(Optional.of(existing));

        AiAnswerResponse result = aiAnswerService.generateOrGetAnswer(null, postId);

        assertThat(result.id()).isEqualTo(answerId);
        assertThat(result.content()).isEqualTo("기존 AI 답변");
        assertThat(result.keyPoints()).containsExactly("포인트 1");
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.suggestedTags()).containsExactly("Java");
        verify(aiAnswerClient, never()).generateAnswer(any(), any());
        verify(aiAnswerRepository, never()).save(any());
    }

    @Test
    @DisplayName("AI 답변이 없으면 FastAPI를 호출하고 전체 필드를 저장 후 반환한다")
    void generateOrGetAnswer_noExisting_callsFastApiAndSavesAllFields() {
        AiAnswer saved = AiAnswer.builder()
                .post(post)
                .content("AI가 생성한 답변")
                .keyPoints(fakeAiResponse.keyPoints())
                .suggestedTags(fakeAiResponse.suggestedTags())
                .confidence(fakeAiResponse.confidence())
                .build();
        UUID answerId = UUID.randomUUID();
        ReflectionTestUtils.setField(saved, "id", answerId);

        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(aiAnswerRepository.findByPost_Id(postId)).willReturn(Optional.empty());
        given(aiQuestionRepository.findByPost_Id(postId)).willReturn(Optional.empty());
        given(aiAnswerClient.generateAnswer(post, null)).willReturn(fakeAiResponse);
        given(aiAnswerRepository.save(any(AiAnswer.class))).willReturn(saved);

        AiAnswerResponse result = aiAnswerService.generateOrGetAnswer(null, postId);

        assertThat(result.id()).isEqualTo(answerId);
        assertThat(result.content()).isEqualTo("AI가 생성한 답변");
        assertThat(result.keyPoints()).containsExactly("핵심 포인트 1", "핵심 포인트 2");
        assertThat(result.suggestedTags()).containsExactly("Spring", "Java");
        assertThat(result.confidence()).isEqualTo(0.88);
        assertThat(result.isAdopted()).isFalse();
        verify(aiAnswerClient).generateAnswer(post, null);
        verify(aiAnswerRepository).save(any(AiAnswer.class));
    }

    @Test
    @DisplayName("커리어 게시글에 AI 답변 요청 시 COMMUNITY_AI_NOT_SUPPORTED 예외가 발생한다")
    void generateOrGetAnswer_careerPost_throwsException() {
        Post careerPost = Post.builder()
                .postType(PostType.CAREER)
                .title("이직 고민")
                .content("커리어 내용")
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(careerPost, "id", postId);

        given(postRepository.findById(postId)).willReturn(Optional.of(careerPost));

        assertThatThrownBy(() -> aiAnswerService.generateOrGetAnswer(null, postId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_AI_NOT_SUPPORTED));

        verify(aiAnswerRepository, never()).save(any());
        verify(aiAnswerClient, never()).generateAnswer(any(), any());
    }

    @Test
    @DisplayName("AiQuestion이 존재하면 refined 데이터를 넘겨서 FastAPI를 호출한다")
    void generateOrGetAnswer_withRefinedQuestion_passesRefinedData() {
        AiQuestion aiQuestion = AiQuestion.builder()
                .post(post)
                .originalTitle("Spring 질문")
                .refinedTitle("Spring IoC란?")
                .refinedContent("IoC 설명 필요")
                .build();
        AiAnswer saved = AiAnswer.builder()
                .post(post)
                .content("refined 기반 답변")
                .keyPoints(fakeAiResponse.keyPoints())
                .suggestedTags(fakeAiResponse.suggestedTags())
                .confidence(fakeAiResponse.confidence())
                .build();
        UUID answerId = UUID.randomUUID();
        ReflectionTestUtils.setField(saved, "id", answerId);

        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(aiAnswerRepository.findByPost_Id(postId)).willReturn(Optional.empty());
        given(aiQuestionRepository.findByPost_Id(postId)).willReturn(Optional.of(aiQuestion));
        given(aiAnswerClient.generateAnswer(post, aiQuestion)).willReturn(fakeAiResponse);
        given(aiAnswerRepository.save(any(AiAnswer.class))).willReturn(saved);

        AiAnswerResponse result = aiAnswerService.generateOrGetAnswer(null, postId);

        assertThat(result.content()).isEqualTo("refined 기반 답변");
        verify(aiAnswerClient).generateAnswer(post, aiQuestion);
    }
}
