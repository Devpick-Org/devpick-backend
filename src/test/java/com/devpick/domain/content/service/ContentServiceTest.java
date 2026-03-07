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

import java.time.LocalDateTime;
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
    }

    @Test
    @DisplayName("getFeed — 태그 없으면 전체 콘텐츠 반환")
    void getFeed_withoutUserTags_returnsAllContents() {
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of());
        given(contentRepository.findByIsAvailableTrueOrderByPublishedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(scrapRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);

        ContentListResponse response = contentService.getFeed(userId, PageRequest.of(0, 20));

        assertThat(response.contents()).hasSize(1);
        assertThat(response.contents().get(0).title()).isEqualTo("Spring Boot 가이드");
    }

    @Test
    @DisplayName("getFeed — 태그 있으면 태그 필터링된 콘텐츠 반환")
    void getFeed_withUserTags_returnsFilteredFeed() {
        UserTag userTag = UserTag.builder()
                .user(user)
                .tag(Tag.builder().name("Spring").build())
                .build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        given(contentRepository.findByTagIdsAndIsAvailableTrue(any(), any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(scrapRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);

        ContentListResponse response = contentService.getFeed(userId, PageRequest.of(0, 20));

        assertThat(response.contents()).hasSize(1);
        verify(contentRepository).findByTagIdsAndIsAvailableTrue(any(), any());
    }

    @Test
    @DisplayName("getDetail — 성공 시 히스토리 저장하고 상세 반환")
    void getDetail_success_savesHistory() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));
        given(scrapRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);

        ContentDetailResponse response = contentService.getDetail(userId, contentId);

        assertThat(response.title()).isEqualTo("Spring Boot 가이드");
        assertThat(response.sourceName()).isEqualTo("Velog");
        verify(historyRepository).save(any(History.class));
    }

    @Test
    @DisplayName("getDetail — 콘텐츠 없으면 CONTENT_NOT_FOUND 예외")
    void getDetail_contentNotFound_throwsException() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> contentService.getDetail(userId, contentId))
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
        verify(historyRepository).save(any(History.class));
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
    @DisplayName("addLike — 좋아요 저장 (히스토리 없음)")
    void addLike_success_savesLikeNoHistory() {
        given(contentRepository.findByIdAndIsAvailableTrue(contentId)).willReturn(Optional.of(content));
        given(likeRepository.existsByUser_IdAndContent_Id(userId, contentId)).willReturn(false);
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.of(user));

        contentService.addLike(userId, contentId);

        verify(likeRepository).save(any(Like.class));
        verify(historyRepository, never()).save(any());
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

        contentService.removeLike(userId, contentId);

        verify(likeRepository).delete(like);
    }

    @Test
    @DisplayName("search — 쿼리와 태그로 검색 결과 반환")
    void search_returnsMatchingContents() {
        given(contentRepository.searchContents(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(content)));
        given(scrapRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);
        given(likeRepository.existsByUser_IdAndContent_Id(any(), any())).willReturn(false);

        ContentListResponse response = contentService.search(userId, "Spring", List.of("Spring"), PageRequest.of(0, 20));

        assertThat(response.contents()).hasSize(1);
        verify(contentRepository).searchContents(any(), any(), any());
    }
}
