package com.devpick.domain.report.service;

import com.devpick.domain.community.entity.Post;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.report.dto.ActivityPageResponse;
import com.devpick.domain.report.dto.HistoryPageResponse;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock
    private HistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private HistoryService historyService;

    private UUID userId;
    private User user;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder().email("test@devpick.kr").nickname("하영").build();
        ReflectionTestUtils.setField(user, "id", userId);
        pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // ============================================================
    // getLearningHistory
    // ============================================================

    @Test
    @DisplayName("학습 히스토리 조회 - 정상 반환 (content 있는 경우)")
    void getLearningHistory_success_withContent() {
        Content content = mock(Content.class);
        UUID contentId = UUID.randomUUID();
        given(content.getId()).willReturn(contentId);
        given(content.getTitle()).willReturn("React useEffect");
        given(content.getPreview()).willReturn("미리보기");

        History history = History.builder()
                .user(user).actionType("content_opened").content(content).build();
        ReflectionTestUtils.setField(history, "id", UUID.randomUUID());

        Page<History> page = new PageImpl<>(List.of(history), pageable, 1);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findLearningHistoryByUserId(eq(userId), any(Pageable.class))).willReturn(page);

        HistoryPageResponse response = historyService.getLearningHistory(userId, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).actionType()).isEqualTo("content_opened");
        assertThat(response.items().get(0).content().title()).isEqualTo("React useEffect");
        assertThat(response.items().get(0).post()).isNull();
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("학습 히스토리 조회 - post 있는 항목도 정상 처리 (content null)")
    void getLearningHistory_historyWithPost_postInfoPopulated() {
        Post post = mock(Post.class);
        UUID postId = UUID.randomUUID();
        given(post.getId()).willReturn(postId);
        given(post.getTitle()).willReturn("useEffect 왜 두 번 실행되나요?");

        History history = History.builder()
                .user(user).actionType("question_created").post(post).content(null).build();
        ReflectionTestUtils.setField(history, "id", UUID.randomUUID());

        Page<History> page = new PageImpl<>(List.of(history), pageable, 1);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findLearningHistoryByUserId(eq(userId), any(Pageable.class))).willReturn(page);

        HistoryPageResponse response = historyService.getLearningHistory(userId, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).post().id()).isEqualTo(postId);
        assertThat(response.items().get(0).post().title()).isEqualTo("useEffect 왜 두 번 실행되나요?");
        assertThat(response.items().get(0).content()).isNull();
    }

    @Test
    @DisplayName("학습 히스토리 조회 - 히스토리 없으면 빈 items 반환")
    void getLearningHistory_emptyHistory_returnsEmptyItems() {
        Page<History> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findLearningHistoryByUserId(eq(userId), any(Pageable.class))).willReturn(emptyPage);

        HistoryPageResponse response = historyService.getLearningHistory(userId, pageable);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(0L);
    }

    @Test
    @DisplayName("학습 히스토리 조회 - 사용자 없으면 USER_NOT_FOUND 예외")
    void getLearningHistory_userNotFound_throwsException() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> historyService.getLearningHistory(userId, pageable))
                .isInstanceOf(DevpickException.class)
                .satisfies(ex -> assertThat(((DevpickException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("학습 히스토리 조회 - content/post 모두 null인 항목 정상 처리")
    void getLearningHistory_historyWithNoContentAndPost_bothNull() {
        History history = History.builder()
                .user(user).actionType("weekly_report_viewed").post(null).content(null).build();
        ReflectionTestUtils.setField(history, "id", UUID.randomUUID());

        Page<History> page = new PageImpl<>(List.of(history), pageable, 1);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findLearningHistoryByUserId(eq(userId), any(Pageable.class))).willReturn(page);

        HistoryPageResponse response = historyService.getLearningHistory(userId, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).content()).isNull();
        assertThat(response.items().get(0).post()).isNull();
    }

    // ============================================================
    // getAllActivity
    // ============================================================

    @Test
    @DisplayName("활동 내역 조회 - content_liked 포함하여 정상 반환")
    void getAllActivity_success_includesContentLiked() {
        Content content = mock(Content.class);
        UUID contentId = UUID.randomUUID();
        given(content.getId()).willReturn(contentId);
        given(content.getTitle()).willReturn("Spring Boot 입문");
        given(content.getPreview()).willReturn("미리보기2");

        History likedHistory = History.builder()
                .user(user).actionType("content_liked").content(content).build();
        ReflectionTestUtils.setField(likedHistory, "id", UUID.randomUUID());

        Page<History> page = new PageImpl<>(List.of(likedHistory), pageable, 1);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findAllActivityByUserId(eq(userId), any(Pageable.class))).willReturn(page);

        ActivityPageResponse response = historyService.getAllActivity(userId, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).activityType()).isEqualTo("content_liked");
        assertThat(response.items().get(0).content().title()).isEqualTo("Spring Boot 입문");
        assertThat(response.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("활동 내역 조회 - post 있는 항목도 정상 처리 (content null)")
    void getAllActivity_historyWithPost_postInfoPopulated() {
        Post post = mock(Post.class);
        UUID postId = UUID.randomUUID();
        given(post.getId()).willReturn(postId);
        given(post.getTitle()).willReturn("Spring 질문입니다");

        History history = History.builder()
                .user(user).actionType("question_created").post(post).content(null).build();
        ReflectionTestUtils.setField(history, "id", UUID.randomUUID());

        Page<History> page = new PageImpl<>(List.of(history), pageable, 1);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findAllActivityByUserId(eq(userId), any(Pageable.class))).willReturn(page);

        ActivityPageResponse response = historyService.getAllActivity(userId, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).post().id()).isEqualTo(postId);
        assertThat(response.items().get(0).post().title()).isEqualTo("Spring 질문입니다");
        assertThat(response.items().get(0).content()).isNull();
    }

    @Test
    @DisplayName("활동 내역 조회 - 활동 없으면 빈 items 반환")
    void getAllActivity_emptyActivity_returnsEmptyItems() {
        Page<History> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findAllActivityByUserId(eq(userId), any(Pageable.class))).willReturn(emptyPage);

        ActivityPageResponse response = historyService.getAllActivity(userId, pageable);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(0L);
    }

    @Test
    @DisplayName("활동 내역 조회 - 사용자 없으면 USER_NOT_FOUND 예외")
    void getAllActivity_userNotFound_throwsException() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> historyService.getAllActivity(userId, pageable))
                .isInstanceOf(DevpickException.class)
                .satisfies(ex -> assertThat(((DevpickException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("활동 내역 조회 - 페이지네이션 메타 정보가 정확히 반환됨")
    void getAllActivity_paginationMeta_isCorrect() {
        Pageable secondPage = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<History> page = new PageImpl<>(List.of(), secondPage, 7);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findAllActivityByUserId(eq(userId), any(Pageable.class))).willReturn(page);

        ActivityPageResponse response = historyService.getAllActivity(userId, secondPage);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isEqualTo(7L);
        assertThat(response.totalPages()).isEqualTo(2);
    }
}
