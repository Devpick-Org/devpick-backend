package com.devpick.domain.community.service;

import com.devpick.domain.community.dto.PostCreateRequest;
import com.devpick.domain.community.dto.PostDetailResponse;
import com.devpick.domain.community.dto.PostListResponse;
import com.devpick.domain.community.dto.PostUpdateRequest;
import com.devpick.domain.community.entity.Answer;
import com.devpick.domain.community.entity.Post;
import com.devpick.domain.community.entity.PostType;
import com.devpick.domain.community.repository.AiAnswerRepository;
import com.devpick.domain.community.repository.AiQuestionRepository;
import com.devpick.domain.community.repository.AnswerLikeRepository;
import com.devpick.domain.community.repository.AnswerRepository;
import com.devpick.domain.community.repository.CommentRepository;
import com.devpick.domain.community.client.AiQuestionCleanupClient;
import com.devpick.domain.community.client.QuestionIndexClient;
import com.devpick.domain.community.repository.PostLikeRepository;
import com.devpick.domain.community.repository.PostRepository;
import com.devpick.global.storage.FileStorageService;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.point.entity.PointAction;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
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
    @Mock
    private com.devpick.domain.point.service.PointService pointService;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private AiAnswerRepository aiAnswerRepository;
    @Mock
    private AiQuestionRepository aiQuestionRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private AnswerLikeRepository answerLikeRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private AiQuestionCleanupClient aiQuestionCleanupClient;
    @Mock
    private QuestionIndexClient questionIndexClient;

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
                .postType(PostType.TECH)
                .title("Test Post")
                .content("Test Content")
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(post, "id", postId);
    }

    @Test
    @DisplayName("createPost — 성공 시 히스토리 저장하고 게시글 반환")
    void createPost_success_savesHistoryAndReturnsPost() {
        PostCreateRequest request = new PostCreateRequest(PostType.TECH, "Test Post", "Test Content", Level.JUNIOR, null);
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
        PostCreateRequest request = new PostCreateRequest(PostType.TECH, "title", "content", Level.JUNIOR, null);
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

        PostListResponse response = postService.getPosts(PageRequest.of(0, 20), null, null);

        assertThat(response.posts()).hasSize(1);
        assertThat(response.posts().get(0).title()).isEqualTo("Test Post");
        assertThat(response.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getPosts — query가 있으면 제목·본문 검색")
    void getPosts_withQuery_usesSearchRepository() {
        given(postRepository.searchByTitleOrContentContaining(eq("Spring"), any()))
                .willReturn(new PageImpl<>(List.of(post)));

        PostListResponse response = postService.getPosts(PageRequest.of(0, 20), "Spring", null);

        assertThat(response.posts()).hasSize(1);
        verify(postRepository).searchByTitleOrContentContaining(eq("Spring"), any());
        verify(postRepository, never()).findAllByOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("getPostDetail — 성공 시 답변 수 포함 상세 반환")
    void getPostDetail_success_returnsDetailWithAnswerCount() {
        given(postRepository.findByIdWithAttachments(postId)).willReturn(Optional.of(post));
        given(answerRepository.countByPost_Id(postId)).willReturn(3L);

        PostDetailResponse response = postService.getPostDetail(postId);

        assertThat(response.title()).isEqualTo("Test Post");
        assertThat(response.answerCount()).isEqualTo(3L);
        assertThat(response.authorNickname()).isEqualTo("tester");
    }

    @Test
    @DisplayName("getPostDetail — 게시글 없으면 COMMUNITY_POST_NOT_FOUND 예외")
    void getPostDetail_notFound_throwsException() {
        given(postRepository.findByIdWithAttachments(postId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostDetail(postId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    @Test
    @DisplayName("updatePost — 성공 시 수정된 게시글 반환")
    void updatePost_success_returnsUpdatedPost() {
        PostUpdateRequest request = new PostUpdateRequest("Updated Title", "Updated Content", Level.SENIOR, null);
        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(answerRepository.countByPost_Id(postId)).willReturn(0L);

        PostDetailResponse response = postService.updatePost(userId, postId, request);

        assertThat(response.title()).isEqualTo("Updated Title");
        assertThat(response.level()).isEqualTo(Level.SENIOR);
    }

    @Test
    @DisplayName("updatePost — 게시글 없으면 COMMUNITY_POST_NOT_FOUND 예외")
    void updatePost_notFound_throwsException() {
        PostUpdateRequest request = new PostUpdateRequest("title", "content", Level.JUNIOR, null);
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
        PostUpdateRequest request = new PostUpdateRequest("title", "content", Level.JUNIOR, null);
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
        verify(aiQuestionCleanupClient).notifyQuestionDeleted(eq(postId));
    }

    @Test
    @DisplayName("deletePost — 게시글 없으면 COMMUNITY_POST_NOT_FOUND 예외")
    void deletePost_notFound_throwsException() {
        given(postRepository.findById(postId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.deletePost(userId, postId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND));
        verify(aiQuestionCleanupClient, never()).notifyQuestionDeleted(any());
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
        verify(aiQuestionCleanupClient, never()).notifyQuestionDeleted(any());
    }

    @Test
    @DisplayName("deletePost — 성공 시 history 먼저 제거 후 자식 삭제 (DP-324)")
    void deletePost_success_deletesAllChildRecords() {
        given(postRepository.findById(postId)).willReturn(Optional.of(post));

        postService.deletePost(userId, postId);

        var order = inOrder(
                pointService,
                historyRepository,
                commentRepository,
                answerLikeRepository,
                answerRepository,
                postLikeRepository,
                aiAnswerRepository,
                aiQuestionRepository,
                postRepository);
        order.verify(pointService).refundLatestByAction(post.getUser(), PointAction.QUESTION_WRITE);
        order.verify(historyRepository).deleteByAnswerPostId(postId);
        order.verify(historyRepository).deleteByPostId(postId);
        order.verify(commentRepository).deleteByPostId(postId);
        order.verify(answerLikeRepository).deleteByPostId(postId);
        order.verify(answerRepository).deleteByPostId(postId);
        order.verify(postLikeRepository).deleteByPostId(postId);
        order.verify(aiAnswerRepository).deleteByPostId(postId);
        order.verify(aiQuestionRepository).deleteByPostId(postId);
        order.verify(postRepository).delete(post);
        verify(aiQuestionCleanupClient).notifyQuestionDeleted(eq(postId));
    }

    @Test
    @DisplayName("createPost — 10초 이내 동일 제목 중복 제출 시 COMMUNITY_DUPLICATE_POST 예외")
    void createPost_duplicatePost_throwsException() {
        PostCreateRequest request = new PostCreateRequest(PostType.TECH, "Test Post", "Test Content", Level.JUNIOR, null);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(postRepository.existsByUser_IdAndTitleAndCreatedAtAfter(eq(userId), eq("Test Post"), any(LocalDateTime.class)))
                .willReturn(true);

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMUNITY_DUPLICATE_POST));
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("getPosts — 게시글 없으면 빈 목록 반환 (배치 조회 미호출)")
    void getPosts_emptyPage_returnsEmptyList() {
        given(postRepository.findAllByOrderByCreatedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of()));

        PostListResponse response = postService.getPosts(PageRequest.of(0, 20), null, null);

        assertThat(response.posts()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(0L);
        verify(answerRepository, never()).countByPostIds(any());
        verify(answerRepository, never()).findByPostIdsOrderByCreatedAtAsc(any());
    }

    @Test
    @DisplayName("getPosts — 답변 수와 첫 번째 답변 미리보기가 포함된 목록 반환")
    void getPosts_withAnswerCountsAndPreviews() {
        Answer answer = Answer.builder()
                .post(post)
                .user(user)
                .content("a".repeat(150))
                .build();

        given(postRepository.findAllByOrderByCreatedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(post)));
        Object[] countRow = {postId, 3L};
        given(answerRepository.countByPostIds(any()))
                .willReturn(Collections.singletonList(countRow));
        given(answerRepository.findByPostIdsOrderByCreatedAtAsc(any()))
                .willReturn(List.of(answer));

        PostListResponse response = postService.getPosts(PageRequest.of(0, 20), null, null);

        assertThat(response.posts()).hasSize(1);
        assertThat(response.posts().get(0).answerCount()).isEqualTo(3L);
        assertThat(response.posts().get(0).topAnswerPreview()).isEqualTo("a".repeat(100) + "...");
    }
}
