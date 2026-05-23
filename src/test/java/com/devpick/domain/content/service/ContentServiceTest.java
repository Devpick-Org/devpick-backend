package com.devpick.domain.content.service;

import com.devpick.domain.content.dto.ContentDetailResponse;
import com.devpick.domain.content.dto.ContentListResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.entity.Like;
import com.devpick.domain.content.entity.Scrap;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.LikeRepository;
import com.devpick.domain.content.repository.ScrapRepository;
import com.devpick.domain.report.entity.History;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.Job;
import com.devpick.domain.user.entity.Level;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.entity.User;
import com.devpick.domain.user.entity.UserTag;
import com.devpick.domain.user.repository.TagRepository;
import com.devpick.domain.user.repository.UserRepository;
import com.devpick.domain.user.repository.UserTagRepository;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    @InjectMocks
    private ContentService contentService;

    @Mock
    private ContentRepository contentRepository;
    @Mock
    private ScrapRepository scrapRepository;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private HistoryRepository historyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserTagRepository userTagRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private com.devpick.domain.point.service.PointService pointService;
    @Mock
    private AiSummaryService aiSummaryService;
    @Mock
    private ContentViewLogService contentViewLogService;
    @Mock
    private com.devpick.domain.content.client.SimilarContentClient similarContentClient;

    private UUID userId;
    private UUID contentId;
    private User user;
    private Content content;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        contentId = UUID.randomUUID();
        user = User.builder()
                .email("test@devpick.kr")
                .nickname("테스터")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();
        ContentSource source = ContentSource.builder()
                .name("Velog")
                .url("https://velog.io")
                .collectMethod("graphql")
                .build();
        content = Content.builder()
                .source(source)
                .title("Spring Boot 가이드")
                .author("홍근")
                .canonicalUrl("https://velog.io/@test/spring")
                .preview("Spring Boot 입문 가이드")
                .publishedAt(LocalDateTime.now())
                .build();
        lenient().when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));
        lenient().when(tagRepository.findByNameIgnoreCaseIn(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("getFeed — 태그 없으면 전체 콘텐츠 반환")
    void getFeed_withoutUserTags_returnsAllContents() {
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of());
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(scrapRepository.findScrappedContentIds(any(), any())).willReturn(List.of());
        given(likeRepository.findLikedContentIds(any(), any())).willReturn(List.of());
        given(aiSummaryService.findCachedCoreSummaries(any(), any())).willReturn(Map.of());

        ContentListResponse response = contentService.getFeed(userId, PageRequest.of(0, 20));

        assertThat(response.contents()).hasSize(1);
        assertThat(response.contents().get(0).title()).isEqualTo("Spring Boot 가이드");
    }

    @Test
    @DisplayName("getFeed — 비로그인(userId null)이면 태그·스크랩·좋아요 조회 없이 전체 피드")
    void getFeed_anonymous_skipsUserScopedQueries() {
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(aiSummaryService.findCachedCoreSummaries(any(), any())).willReturn(Map.of());

        ContentListResponse response = contentService.getFeed(null, PageRequest.of(0, 20));

        assertThat(response.contents()).hasSize(1);
        verify(userTagRepository, never()).findByUser_Id(any());
        verify(scrapRepository, never()).existsByUser_IdAndContent_Id(any(), any());
        verify(likeRepository, never()).existsByUser_IdAndContent_Id(any(), any());
    }

    @Test
    @DisplayName("getFeed — 태그 있으면 랭킹 방식으로 전체 콘텐츠 반환 (관심 태그 우선)")
    void getFeed_withUserTags_returnsRankedFeed() {
        UserTag userTag = UserTag.builder()
                .user(user)
                .tag(Tag.builder().name("Spring").build())
                .build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        given(contentRepository.findAllRankedByTagIds(any(), any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(scrapRepository.findScrappedContentIds(any(), any())).willReturn(List.of());
        given(likeRepository.findLikedContentIds(any(), any())).willReturn(List.of());
        given(aiSummaryService.findCachedCoreSummaries(any(), any())).willReturn(Map.of());

        ContentListResponse response = contentService.getFeed(userId, PageRequest.of(0, 20));

        assertThat(response.contents()).hasSize(1);
        verify(contentRepository).findAllRankedByTagIds(any(), any());
        verify(contentRepository, never()).findByIsAvailableTrueOrderByPublishedAtDesc(any());
    }

    @Test
    @DisplayName("getFeed — 관심 태그와 일치하는 글 없어도 전체 글 반환 (랭킹 방식)")
    void getFeed_withUserTags_noTagMatch_stillReturnsAllContents() {
        UserTag userTag = UserTag.builder()
                .user(user)
                .tag(Tag.builder().name("Rust").build())
                .build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        given(contentRepository.findAllRankedByTagIds(any(), any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(scrapRepository.findScrappedContentIds(any(), any())).willReturn(List.of());
        given(likeRepository.findLikedContentIds(any(), any())).willReturn(List.of());
        given(aiSummaryService.findCachedCoreSummaries(any(), any())).willReturn(Map.of());

        ContentListResponse response = contentService.getFeed(userId, PageRequest.of(0, 20));

        assertThat(response.contents()).hasSize(1);
        verify(contentRepository).findAllRankedByTagIds(any(), any());
        verify(contentRepository, never()).findByIsAvailableTrueOrderByPublishedAtDesc(any());
    }

    @Test
    @DisplayName("getFeed — coreSummary 있으면 preview 대신 coreSummary 사용")
    void getFeed_withCoreSummary_usesCoreSummaryAsPreview() {
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of());
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(scrapRepository.findScrappedContentIds(any(), any())).willReturn(List.of());
        given(likeRepository.findLikedContentIds(any(), any())).willReturn(List.of());
        Map<UUID, String> summaries = new HashMap<>();
        summaries.put(content.getId(), "AI 핵심 요약");
        given(aiSummaryService.findCachedCoreSummaries(any(), any())).willReturn(summaries);

        ContentListResponse response = contentService.getFeed(userId, PageRequest.of(0, 20));

        assertThat(response.contents().get(0).preview()).isEqualTo("AI 핵심 요약");
    }

    @Test
    @DisplayName("getFeed — coreSummary 공백이면 content.preview로 fallback")
    void getFeed_withBlankCoreSummary_fallsBackToContentPreview() {
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of());
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(scrapRepository.findScrappedContentIds(any(), any())).willReturn(List.of());
        given(likeRepository.findLikedContentIds(any(), any())).willReturn(List.of());
        Map<UUID, String> summaries = new HashMap<>();
        summaries.put(content.getId(), "  ");
        given(aiSummaryService.findCachedCoreSummaries(any(), any())).willReturn(summaries);

        ContentListResponse response = contentService.getFeed(userId, PageRequest.of(0, 20));

        assertThat(response.contents().get(0).preview()).isEqualTo("Spring Boot 입문 가이드");
    }

    @Test
    @DisplayName("getDetail — 성공 시 상세만 반환 (content_opened 히스토리는 저장하지 않음)")
    void getDetail_success_doesNotSaveContentOpenedHistory() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(scrapRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);

        ContentDetailResponse response = contentService.getDetail(userId, contentId, "Mozilla/5.0");

        assertThat(response.title()).isEqualTo("Spring Boot 가이드");
        assertThat(response.sourceName()).isEqualTo("Velog");
        verify(historyRepository, never()).save(any(History.class));
    }

    @Test
    @DisplayName("getDetail — 비로그인(userId null)이면 사용자 검증 없이 상세 반환")
    void getDetail_anonymous_success() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));

        ContentDetailResponse response = contentService.getDetail(null, contentId, "Mozilla/5.0");

        assertThat(response.title()).isEqualTo("Spring Boot 가이드");
        verify(userRepository, never()).findByIdAndIsActiveTrue(any());
        verify(contentViewLogService).record(content, null, "Mozilla/5.0");
    }

    @Test
    @DisplayName("recordContentOriginalOpened — content_opened 히스토리 저장")
    void recordContentOriginalOpened_success_savesHistory() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        contentService.recordContentOriginalOpened(userId, contentId);

        ArgumentCaptor<History> captor = ArgumentCaptor.forClass(History.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo("content_opened");
        assertThat(captor.getValue().getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("getDetail — 콘텐츠 없으면 CONTENT_NOT_FOUND 예외")
    void getDetail_contentNotFound_throwsException() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contentService.getDetail(userId, contentId, "Mozilla/5.0"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_NOT_FOUND));
    }

    @Test
    @DisplayName("addScrap — 스크랩 저장 및 히스토리 기록")
    void addScrap_success_savesScrapAndHistory() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(scrapRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        contentService.addScrap(userId, contentId);

        verify(scrapRepository).save(any(Scrap.class));
        ArgumentCaptor<History> captor = ArgumentCaptor.forClass(History.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo("scrapped");
    }

    @Test
    @DisplayName("addScrap — 이미 스크랩이면 CONTENT_ALREADY_SCRAPED 예외")
    void addScrap_alreadyScrapped_throwsException() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(scrapRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(true);

        assertThatThrownBy(() -> contentService.addScrap(userId, contentId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_ALREADY_SCRAPED));
        verify(scrapRepository, never()).save(any());
    }

    @Test
    @DisplayName("removeScrap — 스크랩 삭제 성공")
    void removeScrap_success() {
        Scrap scrap = Scrap.builder().user(user).content(content).build();
        given(scrapRepository.findByUser_IdAndContent_Id(userId, contentId)).willReturn(Optional.of(scrap));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        contentService.removeScrap(userId, contentId);

        verify(scrapRepository).delete(scrap);
    }

    @Test
    @DisplayName("removeScrap — 스크랩 없으면 CONTENT_NOT_SCRAPED 예외")
    void removeScrap_notFound_throwsException() {
        given(scrapRepository.findByUser_IdAndContent_Id(userId, contentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contentService.removeScrap(userId, contentId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_NOT_SCRAPED));
    }

    @Test
    @DisplayName("addLike — 좋아요 저장 및 content_liked 히스토리 기록")
    void addLike_success_savesLikeAndHistory() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(likeRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        contentService.addLike(userId, contentId);

        verify(likeRepository).save(any(Like.class));
        verify(historyRepository).save(any(History.class));
    }

    @Test
    @DisplayName("addLike — 이미 좋아요면 CONTENT_ALREADY_LIKED 예외")
    void addLike_alreadyLiked_throwsException() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(likeRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(true);

        assertThatThrownBy(() -> contentService.addLike(userId, contentId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_ALREADY_LIKED));
    }

    @Test
    @DisplayName("removeLike — 좋아요 삭제 성공")
    void removeLike_success() {
        Like like = Like.builder().user(user).content(content).build();
        given(likeRepository.findByUser_IdAndContent_Id(userId, contentId)).willReturn(Optional.of(like));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        contentService.removeLike(userId, contentId);

        verify(likeRepository).delete(like);
    }

    @Test
    @DisplayName("getDetail — 유저 없으면 USER_NOT_FOUND 예외")
    void getDetail_userNotFound_throwsException() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contentService.getDetail(userId, contentId, "Mozilla/5.0"))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("addScrap — 유저 없으면 USER_NOT_FOUND 예외")
    void addScrap_userNotFound_throwsException() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(scrapRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contentService.addScrap(userId, contentId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("addLike — 유저 없으면 USER_NOT_FOUND 예외")
    void addLike_userNotFound_throwsException() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(likeRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contentService.addLike(userId, contentId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("removeLike — 좋아요 없으면 CONTENT_NOT_LIKED 예외")
    void removeLike_notFound_throwsException() {
        given(likeRepository.findByUser_IdAndContent_Id(userId, contentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contentService.removeLike(userId, contentId))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_NOT_LIKED));
    }

    @Test
    @DisplayName("getDetail — isScrapped/isLiked true 반영")
    void getDetail_withScrapAndLike_flagsTrue() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(scrapRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(true);
        given(likeRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(true);

        ContentDetailResponse response = contentService.getDetail(userId, contentId, "Mozilla/5.0");

        assertThat(response.isScrapped()).isTrue();
        assertThat(response.isLiked()).isTrue();
    }

    @Test
    @DisplayName("search — 결과 없으면 빈 리스트 반환")
    void search_noResult_returnsEmpty() {
        given(contentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .willReturn(new PageImpl<>(List.of()));

        ContentListResponse response = contentService.search(userId, "없는키워드", null, PageRequest.of(0, 20));

        assertThat(response.contents()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    @DisplayName("search — 쿼리와 태그로 검색 결과 반환")
    void search_returnsMatchingContents() {
        given(contentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .willReturn(new PageImpl<>(List.of(content)));
        given(scrapRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(aiSummaryService.findCachedCoreSummary(any(), any())).willReturn(Optional.empty());

        ContentListResponse response = contentService.search(userId, "Spring", List.of("Spring"), PageRequest.of(0, 20));

        assertThat(response.contents()).hasSize(1);
        verify(contentRepository).findAll(
                any(Specification.class),
                eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "publishedAt"))));
    }

    @Test
    @DisplayName("search — 비로그인(userId null)이면 스크랩·좋아요 조회 없이 false")
    void search_nullUserId_noScrapLikeLookup() {
        given(contentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .willReturn(new PageImpl<>(List.of(content)));
        given(aiSummaryService.findCachedCoreSummary(any(), any())).willReturn(Optional.empty());

        ContentListResponse response = contentService.search(null, "Spring", null, PageRequest.of(0, 20));

        assertThat(response.contents()).hasSize(1);
        assertThat(response.contents().get(0).isScrapped()).isFalse();
        assertThat(response.contents().get(0).isLiked()).isFalse();
        verify(scrapRepository, never()).existsByUser_IdAndContent_Id(any(), any());
        verify(likeRepository, never()).existsByUser_IdAndContent_Id(any(), any());
    }

    @Test
    @DisplayName("search — coreSummary 있으면 preview 대신 사용")
    void search_withCoreSummary_usesCoreSummaryAsPreview() {
        given(contentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .willReturn(new PageImpl<>(List.of(content)));
        given(scrapRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(aiSummaryService.findCachedCoreSummary(any(), any())).willReturn(Optional.of("AI 요약"));

        ContentListResponse response = contentService.search(userId, "Spring", null, PageRequest.of(0, 20));

        assertThat(response.contents().get(0).preview()).isEqualTo("AI 요약");
    }

    @Test
    @DisplayName("getRecommendations — 태그 없는 콘텐츠 → 전체 콘텐츠 반환")
    void getRecommendations_noTags_returnsAllContents() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(scrapRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(aiSummaryService.findCachedCoreSummary(any(), any())).willReturn(Optional.empty());

        ContentListResponse response = contentService.getRecommendations(userId, contentId, PageRequest.of(0, 20));

        assertThat(response.contents()).hasSize(1);
        verify(contentRepository).findByIsAvailableTrueOrderByPublishedAtDesc(any());
    }

    @Test
    @DisplayName("getRecommendations — 태그 없어도 현재 글 ID는 추천 목록에서 제외")
    void getRecommendations_noTags_excludesCurrentContentId() {
        ReflectionTestUtils.setField(content, "id", contentId);
        Content other = Content.builder()
                .source(content.getSource())
                .title("다른 글")
                .author("a")
                .canonicalUrl("https://example.com/other")
                .preview("p")
                .publishedAt(LocalDateTime.now().minusDays(1))
                .build();
        UUID otherId = UUID.randomUUID();
        ReflectionTestUtils.setField(other, "id", otherId);

        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(content, other)));
        given(scrapRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(aiSummaryService.findCachedCoreSummary(any(), any())).willReturn(Optional.empty());

        ContentListResponse response = contentService.getRecommendations(userId, contentId, PageRequest.of(0, 5));

        assertThat(response.contents()).hasSize(1);
        assertThat(response.contents().get(0).id()).isEqualTo(otherId);
        assertThat(response.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("getRecommendations — 콘텐츠 없으면 CONTENT_NOT_FOUND 예외")
    void getRecommendations_contentNotFound_throwsException() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contentService.getRecommendations(userId, contentId, PageRequest.of(0, 20)))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CONTENT_NOT_FOUND));
    }

    @Test
    @DisplayName("getRecommendations — AI 서버 성공 시 AI 결과 반환")
    void getRecommendations_aiSuccess_returnsAiResults() {
        UUID aiContentId = UUID.randomUUID();
        Content aiContent = Content.builder()
                .source(content.getSource())
                .title("AI 추천 글")
                .author("a")
                .canonicalUrl("https://velog.io/@ai/rec")
                .preview("AI 추천 미리보기")
                .publishedAt(LocalDateTime.now().minusDays(1))
                .build();
        ReflectionTestUtils.setField(aiContent, "id", aiContentId);

        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(similarContentClient.searchSimilar(eq(contentId), eq(userId), any(), eq(5)))
                .willReturn(List.of(aiContentId));
        given(contentRepository.findAllById(List.of(aiContentId))).willReturn(List.of(aiContent));
        given(scrapRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(aiSummaryService.findCachedCoreSummary(any(), any())).willReturn(Optional.empty());

        ContentListResponse response = contentService.getRecommendations(userId, contentId, PageRequest.of(0, 5));

        assertThat(response.contents()).hasSize(1);
        assertThat(response.contents().get(0).title()).isEqualTo("AI 추천 글");
        verify(contentRepository, never()).findByIsAvailableTrueOrderByPublishedAtDesc(any());
        verify(contentRepository, never()).findRecommendationsByTagIds(any(), any(), any());
    }

    @Test
    @DisplayName("getRecommendations — AI 서버가 빈 결과 반환 시 태그 매칭 fallback")
    void getRecommendations_aiReturnsEmpty_fallsBackToTagMatching() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(similarContentClient.searchSimilar(any(), any(), any(), anyInt())).willReturn(List.of());
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(scrapRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(aiSummaryService.findCachedCoreSummary(any(), any())).willReturn(Optional.empty());

        ContentListResponse response = contentService.getRecommendations(userId, contentId, PageRequest.of(0, 5));

        assertThat(response.contents()).isNotEmpty();
        verify(contentRepository).findByIsAvailableTrueOrderByPublishedAtDesc(any());
    }

    @Test
    @DisplayName("getRecommendations — AI 서버 예외 시 태그 매칭 fallback")
    void getRecommendations_aiThrowsException_fallsBackToTagMatching() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(similarContentClient.searchSimilar(any(), any(), any(), anyInt()))
                .willThrow(new RuntimeException("AI 서버 연결 실패"));
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(scrapRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(aiSummaryService.findCachedCoreSummary(any(), any())).willReturn(Optional.empty());

        ContentListResponse response = contentService.getRecommendations(userId, contentId, PageRequest.of(0, 5));

        assertThat(response.contents()).isNotEmpty();
        verify(contentRepository).findByIsAvailableTrueOrderByPublishedAtDesc(any());
    }

    @Test
    @DisplayName("getDetail — isOriginalVisible false이면 originalContent null 반환")
    void getDetail_isOriginalVisibleFalse_originalContentIsNull() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(scrapRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);

        ContentDetailResponse response = contentService.getDetail(userId, contentId, "Mozilla/5.0");

        // isOriginalVisible 기본값 false → originalContent는 null이어야 함
        assertThat(response.isOriginalVisible()).isFalse();
        assertThat(response.originalContent()).isNull();
    }

    @Test
    @DisplayName("getDetail — 정상 요청 시 contentViewLogService.record 호출")
    void getDetail_success_recordsViewLog() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(scrapRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);

        contentService.getDetail(userId, contentId, "Mozilla/5.0");

        verify(contentViewLogService).record(any(Content.class), eq(userId), eq("Mozilla/5.0"));
    }

    @Test
    @DisplayName("getDetail — 뷰 로그 기록 실패해도 ContentDetailResponse 정상 반환")
    void getDetail_viewLogThrows_stillReturnsDetail() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(scrapRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("DB 오류"))
                .when(contentViewLogService).record(any(), any(), any());

        ContentDetailResponse response = contentService.getDetail(userId, contentId, "Mozilla/5.0");

        assertThat(response.title()).isEqualTo("Spring Boot 가이드");
    }

    // ── getFeed 플랜 제한 ────────────────────────────────────────────────────

    @Test
    @DisplayName("getFeed — 비로그인 유저 offset≥50이면 planLimited:true + 빈 배열")
    void getFeed_anonymous_offsetOver50_returnsPlanLimited() {
        ContentListResponse response = contentService.getFeed(null, PageRequest.of(3, 20)); // offset=60

        assertThat(response.contents()).isEmpty();
        assertThat(response.planLimited()).isTrue();
    }

    @Test
    @DisplayName("getFeed — Free 유저 offset<50이지만 pageSize 초과분 자르기")
    void getFeed_freeUser_offsetNear50_truncatesPageSize() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user)); // FREE 기본값
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of());
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(aiSummaryService.findCachedCoreSummaries(any(), any())).willReturn(Map.of());
        given(scrapRepository.findScrappedContentIds(any(), any())).willReturn(List.of());
        given(likeRepository.findLikedContentIds(any(), any())).willReturn(List.of());

        // offset=40, size=20 → available=10이므로 size가 10으로 잘림
        ContentListResponse response = contentService.getFeed(userId, PageRequest.of(2, 20));

        assertThat(response.planLimited()).isFalse();
        verify(contentRepository).findByIsAvailableTrueOrderByPublishedAtDesc(any());
    }

    @Test
    @DisplayName("getFeed — Pro 유저는 50개 제한 없이 전체 반환")
    void getFeed_proUser_noLimit() {
        User proUser = User.builder()
                .email("pro@devpick.kr").nickname("프로")
                .job(Job.BACKEND).level(Level.JUNIOR)
                .planType(com.devpick.domain.subscription.entity.PlanType.PRO)
                .build();
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(proUser));
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of());
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(aiSummaryService.findCachedCoreSummaries(any(), any())).willReturn(Map.of());
        given(scrapRepository.findScrappedContentIds(any(), any())).willReturn(List.of());
        given(likeRepository.findLikedContentIds(any(), any())).willReturn(List.of());

        // offset=60이어도 Pro는 통과
        ContentListResponse response = contentService.getFeed(userId, PageRequest.of(3, 20));

        assertThat(response.contents()).hasSize(1);
        assertThat(response.planLimited()).isFalse();
    }
}
