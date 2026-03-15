package com.devpick.domain.community.service;

import com.devpick.domain.community.dto.CommentCreateRequest;
import com.devpick.domain.community.dto.CommentResponse;
import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Comment;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.CommentRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @InjectMocks
    private CommentService commentService;

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;

    private UUID userId;
    private UUID postId;
    private UUID answerId;
    private UUID commentId;
    private User user;
    private Post post;
    private Answer answer;
    private Comment comment;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        postId = UUID.randomUUID();
        answerId = UUID.randomUUID();
        commentId = UUID.randomUUID();

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

        comment = Comment.builder()
                .answer(answer)
                .user(user)
                .content("Test Comment")
                .build();
        ReflectionTestUtils.setField(comment, "id", commentId);
    }

    // ─── createComment ───────────────────────────────────────────────────

    @Test
    @DisplayName("createComment — 성공 시 댓글 저장 후 반환")
    void createComment_success_savesAndReturns() {
        CommentCreateRequest request = new CommentCreateRequest("Test Comment");
        given(postRepository.existsById(postId)).willReturn(true);
        given(answerRepository.findById(answerId)).willReturn(Optional.of(answer));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(commentRepository.save(any(Comment.class))).willReturn(comment);

        CommentResponse response = commentService.createComment(userId, postId, answerId, request);

        assertThat(response.content()).isEqualTo("Test Comment");
        assertThat(response.nickname()).isEqualTo("tester");
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    @DisplayName("createComment — 게시글 없으면 COMMUNITY_POST_NOT_FOUND 예외")
    void createComment_postNotFound_throwsException() {
        given(postRepository.existsById(postId)).willReturn(false);

        assertThatThrownBy(() -> commentService.createComment(userId, postId, answerId,
                new CommentCreateRequest("content")))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createComment — 답변 없으면 COMMUNITY_ANSWER_NOT_FOUND 예외")
    void createComment_answerNotFound_throwsException() {
        given(postRepository.existsById(postId)).willReturn(true);
        given(answerRepository.findById(answerId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createComment(userId, postId, answerId,
                new CommentCreateRequest("content")))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createComment — 답변이 다른 게시글 소속이면 COMMUNITY_ANSWER_NOT_FOUND 예외")
    void createComment_answerBelongsToDifferentPost_throwsException() {
        UUID otherPostId = UUID.randomUUID();
        Post otherPost = Post.builder().user(user).title("Other").content("Other").level(Level.JUNIOR).build();
        ReflectionTestUtils.setField(otherPost, "id", otherPostId);
        Answer wrongAnswer = Answer.builder().post(otherPost).user(user).content("Wrong").build();
        ReflectionTestUtils.setField(wrongAnswer, "id", answerId);

        given(postRepository.existsById(postId)).willReturn(true);
        given(answerRepository.findById(answerId)).willReturn(Optional.of(wrongAnswer));

        assertThatThrownBy(() -> commentService.createComment(userId, postId, answerId,
                new CommentCreateRequest("content")))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createComment — 유저 없으면 USER_NOT_FOUND 예외")
    void createComment_userNotFound_throwsException() {
        given(postRepository.existsById(postId)).willReturn(true);
        given(answerRepository.findById(answerId)).willReturn(Optional.of(answer));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createComment(userId, postId, answerId,
                new CommentCreateRequest("content")))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
        verify(commentRepository, never()).save(any());
    }

    // ─── deleteComment ───────────────────────────────────────────────────

    @Test
    @DisplayName("deleteComment — 성공 시 댓글 삭제")
    void deleteComment_success_deletesComment() {
        given(postRepository.existsById(postId)).willReturn(true);
        given(answerRepository.findById(answerId)).willReturn(Optional.of(answer));
        given(commentRepository.findById(commentId)).willReturn(Optional.of(comment));

        commentService.deleteComment(userId, postId, answerId, commentId);

        verify(commentRepository).delete(comment);
    }

    @Test
    @DisplayName("deleteComment — 게시글 없으면 COMMUNITY_POST_NOT_FOUND 예외")
    void deleteComment_postNotFound_throwsException() {
        given(postRepository.existsById(postId)).willReturn(false);

        assertThatThrownBy(() -> commentService.deleteComment(userId, postId, answerId, commentId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        verify(commentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteComment — 답변 없으면 COMMUNITY_ANSWER_NOT_FOUND 예외")
    void deleteComment_answerNotFound_throwsException() {
        given(postRepository.existsById(postId)).willReturn(true);
        given(answerRepository.findById(answerId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.deleteComment(userId, postId, answerId, commentId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_ANSWER_NOT_FOUND));
        verify(commentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteComment — 댓글 없으면 COMMUNITY_COMMENT_NOT_FOUND 예외")
    void deleteComment_commentNotFound_throwsException() {
        given(postRepository.existsById(postId)).willReturn(true);
        given(answerRepository.findById(answerId)).willReturn(Optional.of(answer));
        given(commentRepository.findById(commentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.deleteComment(userId, postId, answerId, commentId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
        verify(commentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteComment — 댓글이 다른 답변 소속이면 COMMUNITY_COMMENT_NOT_FOUND 예외")
    void deleteComment_commentBelongsToDifferentAnswer_throwsException() {
        UUID otherAnswerId = UUID.randomUUID();
        Answer otherAnswer = Answer.builder().post(post).user(user).content("Other").build();
        ReflectionTestUtils.setField(otherAnswer, "id", otherAnswerId);
        Comment wrongComment = Comment.builder().answer(otherAnswer).user(user).content("Wrong").build();
        ReflectionTestUtils.setField(wrongComment, "id", commentId);

        given(postRepository.existsById(postId)).willReturn(true);
        given(answerRepository.findById(answerId)).willReturn(Optional.of(answer));
        given(commentRepository.findById(commentId)).willReturn(Optional.of(wrongComment));

        assertThatThrownBy(() -> commentService.deleteComment(userId, postId, answerId, commentId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
        verify(commentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteComment — 작성자 아니면 COMMUNITY_UNAUTHORIZED_COMMENT_ACTION 예외")
    void deleteComment_unauthorized_throwsException() {
        UUID otherUserId = UUID.randomUUID();
        given(postRepository.existsById(postId)).willReturn(true);
        given(answerRepository.findById(answerId)).willReturn(Optional.of(answer));
        given(commentRepository.findById(commentId)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment(otherUserId, postId, answerId, commentId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_UNAUTHORIZED_COMMENT_ACTION));
        verify(commentRepository, never()).delete(any());
    }
}
