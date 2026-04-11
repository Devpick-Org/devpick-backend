package com.devpick.domain.community.service;

import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.repository.AnswerLikeRepository;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.PostLikeRepository;
import com.devpick.domain.community.repository.PostRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommunityLikeServiceTest {

    @InjectMocks
    private CommunityLikeService communityLikeService;

    @Mock
    private PostRepository postRepository;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private AnswerLikeRepository answerLikeRepository;
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
        user = User.builder().email("a@b.c").nickname("n").build();
        ReflectionTestUtils.setField(user, "id", userId);
        post = Post.builder().user(user).title("t").content("c").level(Level.JUNIOR).build();
        ReflectionTestUtils.setField(post, "id", postId);
        answer = Answer.builder().post(post).user(user).content("ans").build();
        ReflectionTestUtils.setField(answer, "id", answerId);
    }

    @Test
    @DisplayName("addPostLike — 성공 시 저장")
    void addPostLike_success() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(postLikeRepository.existsByPost_IdAndUser_Id(postId, userId)).willReturn(false);

        communityLikeService.addPostLike(userId, postId);

        verify(postLikeRepository).save(any());
    }

    @Test
    @DisplayName("addPostLike — 이미 좋아요면 409")
    void addPostLike_duplicate_throws() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(postLikeRepository.existsByPost_IdAndUser_Id(postId, userId)).willReturn(true);

        assertThatThrownBy(() -> communityLikeService.addPostLike(userId, postId))
                .isInstanceOf(DevpickException.class)
                .satisfies(ex -> assertThat(((DevpickException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_POST_ALREADY_LIKED));
    }

    @Test
    @DisplayName("removePostLike — 좋아요 없으면 404")
    void removePostLike_notFound_throws() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(postLikeRepository.findByPost_IdAndUser_Id(postId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> communityLikeService.removePostLike(userId, postId))
                .isInstanceOf(DevpickException.class)
                .satisfies(ex -> assertThat(((DevpickException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_LIKED));
    }

    @Test
    @DisplayName("addAnswerLike — postId 불일치면 답변 없음")
    void addAnswerLike_wrongPost_throws() {
        UUID otherPost = UUID.randomUUID();
        Post other = Post.builder().user(user).title("x").content("y").level(Level.JUNIOR).build();
        ReflectionTestUtils.setField(other, "id", otherPost);
        Answer wrongPostAnswer = Answer.builder().post(other).user(user).content("a").build();
        ReflectionTestUtils.setField(wrongPostAnswer, "id", answerId);

        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(answerRepository.findById(answerId)).willReturn(Optional.of(wrongPostAnswer));

        assertThatThrownBy(() -> communityLikeService.addAnswerLike(userId, postId, answerId))
                .isInstanceOf(DevpickException.class)
                .satisfies(ex -> assertThat(((DevpickException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));

        verify(answerLikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("addAnswerLike — 성공 시 저장")
    void addAnswerLike_success() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(answerRepository.findById(answerId)).willReturn(Optional.of(answer));
        given(answerLikeRepository.existsByAnswer_IdAndUser_Id(answerId, userId)).willReturn(false);

        communityLikeService.addAnswerLike(userId, postId, answerId);

        verify(answerLikeRepository).save(any());
    }
}
