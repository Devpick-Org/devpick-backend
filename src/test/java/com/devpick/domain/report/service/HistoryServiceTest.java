package com.devpick.domain.report.service;

import com.devpick.domain.community.entity.Post;
import com.devpick.domain.content.entity.Content;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    // getHistory
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
        UUID historyId = UUID.randomUUID();
        ReflectionTestUtils.setField(history, "id", historyId);

        Page<UUID> idPage = new PageImpl<>(List.of(historyId), pageable, 1);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findHistoryIdsExcludingContentLiked(eq(userId), any(Pageable.class)))
                .willReturn(idPage);
        given(historyRepository.findHistoriesWithAssociationsByIds(List.of(historyId)))
                .willReturn(List.of(history));

        HistoryPageResponse response = historyService.getHistory(userId, null, null, null, pageable);

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
        UUID historyId = UUID.randomUUID();
        ReflectionTestUtils.setField(history, "id", historyId);

        Page<UUID> idPage = new PageImpl<>(List.of(historyId), pageable, 1);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findHistoryIdsExcludingContentLiked(eq(userId), any(Pageable.class)))
                .willReturn(idPage);
        given(historyRepository.findHistoriesWithAssociationsByIds(List.of(historyId)))
                .willReturn(List.of(history));

        HistoryPageResponse response = historyService.getHistory(userId, null, null, null, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).post().id()).isEqualTo(postId);
        assertThat(response.items().get(0).post().title()).isEqualTo("useEffect 왜 두 번 실행되나요?");
        assertThat(response.items().get(0).content()).isNull();
    }

    @Test
    @DisplayName("학습 히스토리 조회 - 히스토리 없으면 빈 items 반환 (2단계 쿼리 호출 안 됨)")
    void getLearningHistory_emptyHistory_returnsEmptyItems() {
        Page<UUID> emptyIdPage = new PageImpl<>(List.of(), pageable, 0);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findHistoryIdsExcludingContentLiked(eq(userId), any(Pageable.class)))
                .willReturn(emptyIdPage);

        HistoryPageResponse response = historyService.getHistory(userId, null, null, null, pageable);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(0L);
        verify(historyRepository, never()).findHistoriesWithAssociationsByIds(any());
    }

    @Test
    @DisplayName("학습 히스토리 조회 - 사용자 없으면 USER_NOT_FOUND 예외")
    void getLearningHistory_userNotFound_throwsException() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> historyService.getHistory(userId, null, null, null, pageable))
                .isInstanceOf(DevpickException.class)
                .satisfies(ex -> assertThat(((DevpickException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("학습 히스토리 조회 - content/post 모두 null인 항목 정상 처리")
    void getLearningHistory_historyWithNoContentAndPost_bothNull() {
        History history = History.builder()
                .user(user).actionType("weekly_report_viewed").post(null).content(null).build();
        UUID historyId = UUID.randomUUID();
        ReflectionTestUtils.setField(history, "id", historyId);

        Page<UUID> idPage = new PageImpl<>(List.of(historyId), pageable, 1);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findHistoryIdsExcludingContentLiked(eq(userId), any(Pageable.class)))
                .willReturn(idPage);
        given(historyRepository.findHistoriesWithAssociationsByIds(List.of(historyId)))
                .willReturn(List.of(history));

        HistoryPageResponse response = historyService.getHistory(userId, null, null, null, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).content()).isNull();
        assertThat(response.items().get(0).post()).isNull();
    }

    @ParameterizedTest(name = "{0} → {1}p")
    @MethodSource("actionTypePointsProvider")
    @DisplayName("actionType별 points 매핑 검증")
    void historyItemResponse_of_pointsMappedCorrectly(String actionType, Integer expectedPoints) {
        History history = History.builder()
                .user(user).actionType(actionType).build();
        UUID historyId = UUID.randomUUID();
        ReflectionTestUtils.setField(history, "id", historyId);

        Page<UUID> idPage = new PageImpl<>(List.of(historyId), pageable, 1);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findHistoryIdsExcludingContentLiked(eq(userId), any(Pageable.class)))
                .willReturn(idPage);
        given(historyRepository.findHistoriesWithAssociationsByIds(List.of(historyId)))
                .willReturn(List.of(history));

        HistoryPageResponse response = historyService.getHistory(userId, null, null, null, pageable);

        assertThat(response.items().get(0).points()).isEqualTo(expectedPoints);
    }

    static Stream<Arguments> actionTypePointsProvider() {
        return Stream.of(
                Arguments.of("ai_summary_viewed", 3),
                Arguments.of("scrapped", 5),
                Arguments.of("content_liked", 2),
                Arguments.of("question_created", 10),
                Arguments.of("answer_written", 15),
                Arguments.of("answer_adopted", 30),
                Arguments.of("daily_login", 1),
                Arguments.of("ai_quiz_completed", 5),
                Arguments.of("content_opened", null),
                Arguments.of("post_created", null)
        );
    }

    @Test
    @DisplayName("actionTypes 필터 있을 때 ID 필터 쿼리가 호출된다")
    void getLearningHistory_withActionTypes_usesActionTypeQuery() {
        List<String> actionTypes = List.of("content_opened");

        History history = History.builder()
                .user(user).actionType("content_opened").build();
        UUID historyId = UUID.randomUUID();
        ReflectionTestUtils.setField(history, "id", historyId);

        Page<UUID> idPage = new PageImpl<>(List.of(historyId), pageable, 1);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(historyRepository.findHistoryIdsByActionTypes(
                eq(userId), eq(actionTypes), any(Pageable.class)))
                .willReturn(idPage);
        given(historyRepository.findHistoriesWithAssociationsByIds(List.of(historyId)))
                .willReturn(List.of(history));

        HistoryPageResponse response = historyService.getHistory(userId, actionTypes, null, null, pageable);

        assertThat(response.items()).hasSize(1);
        verify(historyRepository).findHistoryIdsByActionTypes(
                eq(userId), eq(actionTypes), any(Pageable.class));
        verify(historyRepository, never()).findHistoryIdsExcludingContentLiked(any(), any());
    }
}
