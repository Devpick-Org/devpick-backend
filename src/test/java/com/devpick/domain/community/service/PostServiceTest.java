package com.devpick.domain.community.service;

import com.devpick.domain.community.dto.PostCreateRequest;
import com.devpick.domain.community.dto.PostDetailResponse;
import com.devpick.domain.community.dto.PostListResponse;
import com.devpick.domain.community.dto.PostUpdateRequest;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
class PostServiceTest {

    @InjectMocks
    private PostService postService;

    @Mock
    private PostRepository postRepository;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HistoryRepository historyRepository;

    private UUID userId;
    private UUID postId;
    private User user;
    private Post post;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        postId = UUID.randomUUID();

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
    }

    @Test
    @DisplayName("createPost — 성공 시 히스토리 저장하고 게시글 반환")
    void createPost_success_savesHistoryAndReturnsPost() {
        PostCreateRequest request = new PostCreateRequest("Test Post", "Test Content", Level.JUNIOR);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(postRepository.save(any(Post.class))).willReturn(post);

        PostDetailResponse response = postService.createPost(userId, request);

        assertThat(response.title()).isEqualTo("Test Post");
        assertThat(response.answerCount()).isEqualTo(0L);
        verify(historyRepository).save(any(History.class));
    }

    @Test
    @DisplayName("createPost — 사용자 없으면 USER_NOT_FOUND 예외")
    void createPost_userNotFound_throwsException() {
        PostCreateRequest request = new PostCreateRequest("title", "content", Level.JUNIOR);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("getPosts — 최신순 목록 반환")
    void getPosts_returnsPagedList() {
        given(postRepository.findAllByOrderByCreatedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(post)));

        PostListResponse response = postService.getPosts(PageRequest.of(0, 20));

        assertThat(response.posts()).hasSize(1);
        assertThat(response.posts().get(0).title()).isEqualTo("Test Post");
        assertThat(response.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getPostDetail — 성공 시 답변 수 포함 상세 반환")
    void getPostDetail_success_returnsDetailWithAnswerCount() {
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(answerRepository.countByPost_Id(postId)).willReturn(3L);

        PostDetailResponse response = postService.getPostDetail(postId);

        assertThat(response.title()).isEqualTo("Test Post");
        assertThat(response.answerCount()).isEqualTo(3L);
        assertThat(response.authorNickname()).isEqualTo("tester");
    }

    @Test
    @DisplayName("getPostDetail — 게시글 없으면 COMMUNITY_POST_NOT_FOUND 예외")
    void getPostDetail_notFound_throwsException() {
        given(postRepository.findById(postId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostDetail(postId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    @Test
    @DisplayName("updatePost — 성공 시 수정된 게시글 반환")
    void updatePost_success_returnsUpdatedPost() {
        PostUpdateRequest request = new PostUpdateRequest("Updated Title", "Updated Content", Level.SENIOR);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(answerRepository.countByPost_Id(postId)).willReturn(0L);

        PostDetailResponse response = postService.updatePost(userId, postId, request);

        assertThat(response.title()).isEqualTo("Updated Title");
        assertThat(response.level()).isEqualTo(Level.SENIOR);
    }

    @Test
    @DisplayName("updatePost — 게시글 없으면 COMMUNITY_POST_NOT_FOUND 예외")
    void updatePost_notFound_throwsException() {
        PostUpdateRequest request = new PostUpdateRequest("title", "content", Level.JUNIOR);
        given(postRepository.findById(postId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.updatePost(userId, postId, request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    @Test
    @DisplayName("updatePost — 작성자 아닌 경우 COMMUNITY_UNAUTHORIZED_POST_ACTION 예외")
    void updatePost_unauthorized_throwsException() {
        UUID otherUserId = UUID.randomUUID();
        PostUpdateRequest request = new PostUpdateRequest("title", "content", Level.JUNIOR);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updatePost(otherUserId, postId, request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_UNAUTHORIZED_POST_ACTION));
    }

    @Test
    @DisplayName("deletePost — 성공 시 게시글 삭제")
    void deletePost_success_deletesPost() {
        given(postRepository.findById(postId)).willReturn(Optional.of(post));

        postService.deletePost(userId, postId);

        verify(postRepository).delete(post);
    }

    @Test
    @DisplayName("deletePost — 게시글 없으면 COMMUNITY_POST_NOT_FOUND 예외")
    void deletePost_notFound_throwsException() {
        given(postRepository.findById(postId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.deletePost(userId, postId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    @Test
    @DisplayName("deletePost — 작성자 아닌 경우 COMMUNITY_UNAUTHORIZED_POST_ACTION 예외")
    void deletePost_unauthorized_throwsException() {
        UUID otherUserId = UUID.randomUUID();
        given(postRepository.findById(postId)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deletePost(otherUserId, postId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_UNAUTHORIZED_POST_ACTION));
        verify(postRepository, never()).delete(any());
    }
}
