package com.devpick.domain.content.service;

import com.devpick.domain.content.dto.ScrapItemResponse;
import com.devpick.domain.content.dto.ScrapListResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.entity.Scrap;
import com.devpick.domain.content.repository.AiSummaryRepository;
import com.devpick.domain.content.repository.ScrapRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScrapServiceTest {

    @InjectMocks private ScrapService scrapService;
    @Mock private ScrapRepository scrapRepository;
    @Mock private UserRepository userRepository;
    @Mock private AiSummaryRepository aiSummaryRepository;

    private UUID userId;
    private User user;
    private Scrap scrap;
    private UUID contentId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        contentId = UUID.randomUUID();

        ContentSource source = ContentSource.builder()
                .name("velog").url("https://velog.io").collectMethod("rss").build();
        ReflectionTestUtils.setField(source, "id", UUID.randomUUID());

        Content content = Content.builder()
                .source(source)
                .title("Spring Boot 완전 정복")
                .canonicalUrl("https://velog.io/@test/spring")
                .preview("원문 미리보기 텍스트")
                .thumbnailUrl("https://thumb.jpg")
                .build();
        ReflectionTestUtils.setField(content, "id", contentId);

        user = User.builder()
                .email("test@devpick.kr")
                .nickname("tester")
                .job(Job.BACKEND)
                .level(Level.JUNIOR)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);

        scrap = Scrap.builder().user(user).content(content).build();
        ReflectionTestUtils.setField(scrap, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(scrap, "createdAt", LocalDateTime.now());

        lenient().when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));
        lenient().when(aiSummaryRepository.batchFindCoreSummaries(anyList(), anyString())).thenReturn(Map.of());
    }

    @Test
    @DisplayName("존재하지 않는 유저 → USER_NOT_FOUND 예외")
    void getScraps_userNotFound_throwsException() {
        given(userRepository.findByIdAndIsActiveTrue(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> scrapService.getScraps(userId, null, "newest", Pageable.ofSize(10)))
                .isInstanceOf(DevpickException.class)
                .satisfies(e -> assertThat(((DevpickException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("스크랩 없는 유저 → 빈 리스트 반환")
    void getScraps_emptyResult_returnsEmptyList() {
        given(scrapRepository.findScraps(any(), any()))
                .willReturn(Page.empty());

        ScrapListResponse result = scrapService.getScraps(userId, null, "newest", Pageable.ofSize(10));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("스크랩 있는 유저 → 항목 반환")
    void getScraps_withScraps_returnsItems() {
        given(scrapRepository.findScraps(any(), any()))
                .willReturn(new PageImpl<>(List.of(scrap)));

        ScrapListResponse result = scrapService.getScraps(userId, null, "newest", Pageable.ofSize(10));

        assertThat(result.content()).hasSize(1);
        ScrapItemResponse item = result.content().get(0);
        assertThat(item.contentId()).isEqualTo(contentId);
        assertThat(item.title()).isEqualTo("Spring Boot 완전 정복");
        assertThat(item.sourceName()).isEqualTo("velog");
    }

    @Test
    @DisplayName("sort=oldest → createdAt ASC 정렬 적용")
    void getScraps_oldestSort_appliesAscSort() {
        given(scrapRepository.findScraps(any(), any()))
                .willReturn(Page.empty());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        scrapService.getScraps(userId, null, "oldest", Pageable.ofSize(10));

        verify(scrapRepository).findScraps(any(), pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("sort=newest → createdAt DESC 정렬 적용")
    void getScraps_newestSort_appliesDescSort() {
        given(scrapRepository.findScraps(any(), any()))
                .willReturn(Page.empty());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        scrapService.getScraps(userId, null, "newest", Pageable.ofSize(10));

        verify(scrapRepository).findScraps(any(), pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("AI 요약 있으면 summary 필드에 AI 요약 반환")
    void getScraps_withAiSummary_returnsSummary() {
        given(scrapRepository.findScraps(any(), any()))
                .willReturn(new PageImpl<>(List.of(scrap)));
        given(aiSummaryRepository.batchFindCoreSummaries(anyList(), anyString()))
                .willReturn(Map.of(contentId, "AI 핵심 요약"));

        ScrapListResponse result = scrapService.getScraps(userId, null, "newest", Pageable.ofSize(10));

        assertThat(result.content().get(0).summary()).isEqualTo("AI 핵심 요약");
    }

    @Test
    @DisplayName("AI 요약 없으면 원문 미리보기(preview) fallback")
    void getScraps_noAiSummary_returnsPreviewFallback() {
        given(scrapRepository.findScraps(any(), any()))
                .willReturn(new PageImpl<>(List.of(scrap)));
        given(aiSummaryRepository.batchFindCoreSummaries(anyList(), anyString()))
                .willReturn(Map.of());

        ScrapListResponse result = scrapService.getScraps(userId, null, "newest", Pageable.ofSize(10));

        assertThat(result.content().get(0).summary()).isEqualTo("원문 미리보기 텍스트");
    }

    @Test
    @DisplayName("검색어 있으면 findScrapsWithSearch 호출")
    void getScraps_withQuery_callsFindScrapsWithSearch() {
        given(scrapRepository.findScrapsWithSearch(any(), eq("spring"), any()))
                .willReturn(new PageImpl<>(List.of(scrap)));

        ScrapListResponse result = scrapService.getScraps(userId, "spring", "newest", Pageable.ofSize(10));

        assertThat(result.content()).hasSize(1);
        verify(scrapRepository).findScrapsWithSearch(eq(userId), eq("spring"), any());
    }

    @Test
    @DisplayName("DynamoDB 예외 시 원문 미리보기로 fallback")
    void getScraps_dynamoDbException_returnsPreviewFallback() {
        given(scrapRepository.findScraps(any(), any()))
                .willReturn(new PageImpl<>(List.of(scrap)));
        given(aiSummaryRepository.batchFindCoreSummaries(anyList(), anyString()))
                .willThrow(new RuntimeException("DynamoDB 연결 실패"));

        ScrapListResponse result = scrapService.getScraps(userId, null, "newest", Pageable.ofSize(10));

        assertThat(result.content().get(0).summary()).isEqualTo("원문 미리보기 텍스트");
    }
}
