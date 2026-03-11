package com.devpick.domain.community.service;

import com.devpick.domain.community.dto.AnswerCreateRequest;
import com.devpick.domain.community.dto.AnswerResponse;
import com.devpick.domain.community.dto.AnswerUpdateRequest;
import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
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
class AnswerServiceTest {

    @InjectMocks
    private AnswerService answerService;

    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;

    private UUID userId;
    private UUID postId;
    private UUID answerId;
    private User user;
    private Post post;
    private Answer answer;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        postId = UUID.randomUUID();
        answerId = UUID.randomUUID();

        user = User.builder()
                .email("test@devpick.kr")
                .nickname("tester")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);

        post = Post.builder()
                .user(user)
                .title("Test Post")
                .content("Test Content")
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(post, "id", postId);

        answer = Answer.builder()
                .post(post)
                .user(user)
                .content("Test Answer")
                .build();
        ReflectionTestUtils.setField(answer, "id", answerId);
    }

    @Test
    @DisplayName("createAnswer — 성공 시 답변 저장 후 반환")
    void createAnswer_success_savesAndReturns() {
        AnswerCreateRequest request = new AnswerCreateRequest("Test Answer");
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(answerRepository.save(any(Answer.class))).willReturn(answer);

        AnswerResponse response = answerService.createAnswer(userId, postId, request);

        assertThat(response.content()).isEqualTo("Test Answer");
        assertThat(response.isAdopted()).isFalse();
        verify(answerRepository).save(any(Answer.class));
    }

    @Test
    @DisplayName("createAnswer — 게시글 없으면 COMMUNITY_POST_NOT_FOUND 예외")
    void createAnswer_postNotFound_throwsException() {
        given(postRepository.findById(postId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> answerService.createAnswer(userId, postId, new AnswerCreateRequest("content")))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        verify(answerRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateAnswer — 성공 시 수정된 답변 반환")
    void updateAnswer_success_returnsUpdatedAnswer() {
        AnswerUpdateRequest request = new AnswerUpdateRequest("Updated Answer");
        given(answerRepository.findById(answerId)).willReturn(Optional.of(answer));

        AnswerResponse response = answerService.updateAnswer(userId, postId, answerId, request);

        assertThat(response.content()).isEqualTo("Updated Answer");
    }

    @Test
    @DisplayName("updateAnswer — 작성자 아닌 경우 COMMUNITY_UNAUTHORIZED_ANSWER_ACTION 예외")
    void updateAnswer_unauthorized_throwsException() {
        UUID otherUserId = UUID.randomUUID();
        given(answerRepository.findById(answerId)).willReturn(Optional.of(answer));

        assertThatThrownBy(() -> answerService.updateAnswer(otherUserId, postId, answerId, new AnswerUpdateRequest("x")))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_UNAUTHORIZED_ANSWER_ACTION));
    }

    @Test
    @DisplayName("updateAnswer — 답변 없으면 COMMUNITY_ANSWER_NOT_FOUND 예외")
    void updateAnswer_notFound_throwsException() {
        given(answerRepository.findById(answerId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> answerService.updateAnswer(userId, postId, answerId, new AnswerUpdateRequest("x")))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));
    }

    @Test
    @DisplayName("deleteAnswer — 성공 시 답변 삭제")
    void deleteAnswer_success_deletesAnswer() {
        given(answerRepository.findById(answerId)).willReturn(Optional.of(answer));

        answerService.deleteAnswer(userId, postId, answerId);

        verify(answerRepository).delete(answer);
    }

    @Test
    @DisplayName("deleteAnswer — 작성자 아닌 경우 COMMUNITY_UNAUTHORIZED_ANSWER_ACTION 예외")
    void deleteAnswer_unauthorized_throwsException() {
        UUID otherUserId = UUID.randomUUID();
        given(answerRepository.findById(answerId)).willReturn(Optional.of(answer));

        assertThatThrownBy(() -> answerService.deleteAnswer(otherUserId, postId, answerId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_UNAUTHORIZED_ANSWER_ACTION));
        verify(answerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("adoptAnswer — 성공 시 채택된 답변 반환")
    void adoptAnswer_success_adoptsAnswer() {
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(answerRepository.findAdoptedByPostIdForUpdate(postId)).willReturn(List.of());
        given(answerRepository.findById(answerId)).willReturn(Optional.of(answer));

        AnswerResponse response = answerService.adoptAnswer(userId, postId, answerId);

        assertThat(response.isAdopted()).isTrue();
    }

    @Test
    @DisplayName("adoptAnswer — 이미 채택된 답변 있으면 COMMUNITY_ALREADY_ADOPTED 예외")
    void adoptAnswer_alreadyAdopted_throwsException() {
        Answer adoptedAnswer = Answer.builder().post(post).user(user).content("adopted").build();
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(answerRepository.findAdoptedByPostIdForUpdate(postId)).willReturn(List.of(adoptedAnswer));

        assertThatThrownBy(() -> answerService.adoptAnswer(userId, postId, answerId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_ALREADY_ADOPTED));
    }

    @Test
    @DisplayName("adoptAnswer — 게시글 작성자 아니면 COMMUNITY_ONLY_POST_AUTHOR_CAN_ADOPT 예외")
    void adoptAnswer_notPostAuthor_throwsException() {
        UUID otherUserId = UUID.randomUUID();
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(answerRepository.findAdoptedByPostIdForUpdate(postId)).willReturn(List.of());

        assertThatThrownBy(() -> answerService.adoptAnswer(otherUserId, postId, answerId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_ONLY_POST_AUTHOR_CAN_ADOPT));
    }

    @Test
    @DisplayName("adoptAnswer — 답변 없으면 COMMUNITY_ANSWER_NOT_FOUND 예외")
    void adoptAnswer_answerNotFound_throwsException() {
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(answerRepository.findAdoptedByPostIdForUpdate(postId)).willReturn(List.of());
        given(answerRepository.findById(answerId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> answerService.adoptAnswer(userId, postId, answerId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));
    }
}
