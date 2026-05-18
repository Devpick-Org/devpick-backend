package com.devpick.domain.content.service;

import com.devpick.domain.content.dto.RecommendContentsResponse;
import com.devpick.domain.content.dto.YoutubeRecommendItem;
import com.devpick.domain.content.dto.YoutubeRecommendResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.entity.ContentTag;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.LikeRepository;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.entity.UserTag;
import com.devpick.domain.user.repository.TagRepository;
import com.devpick.domain.user.repository.UserTagRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendServiceTest {

    @InjectMocks private RecommendService recommendService;

    @Mock private HistoryRepository historyRepository;
    @Mock private ContentRepository contentRepository;
    @Mock private UserTagRepository userTagRepository;
    @Mock private TagRepository tagRepository;
    @Mock private LikeRepository likeRepository;
    @Mock private AiSummaryService aiSummaryService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ValueOperations<String, String> valueOps;

    private UUID userId;
    private List<Content> tenContents;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        ContentSource source = ContentSource.builder()
                .name("Velog").url("https://velog.io").collectMethod("graphql").build();
        tenContents = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Content c = Content.builder()
                    .source(source)
                    .title("글 " + i)
                    .author("작성자")
                    .canonicalUrl("https://velog.io/@test/" + i)
                    .preview("미리보기 " + i)
                    .publishedAt(LocalDateTime.now().minusDays(i))
                    .build();
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            tenContents.add(c);
        }

        lenient().when(aiSummaryService.findCachedCoreSummary(any(), any())).thenReturn(Optional.empty());
        lenient().when(likeRepository.existsByUser_IdAndContent_Id(any(), any())).thenReturn(false);
    }

    // ─── 글 추천 테스트 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("history 태그 기반 후보 10개 이상 → 개인화 응답, isPersonalized=true")
    void getRecommendContents_historyTags_returnsPersonalized() throws JsonProcessingException {
        List<UUID> tagIds = List.of(UUID.randomUUID());
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagIds);
        given(objectMapper.writeValueAsString(any())).willReturn("[\"uuid\"]");
        Tag tag = Tag.builder().name("Java").build();
        given(tagRepository.findAllById(any())).willReturn(List.of(tag));
        given(contentRepository.findByTagNameInTitleExcludingYoutubeAndScrapped(anyString(), eq(userId), any()))
                .willReturn(tenContents);

        RecommendContentsResponse result = recommendService.getRecommendContents(userId);

        assertThat(result.isPersonalized()).isTrue();
        assertThat(result.message()).isNull();
        assertThat(result.contents()).hasSize(8);
        verify(userTagRepository, never()).findByUser_Id(any());
    }

    @Test
    @DisplayName("history 태그 기반 후보 10개 미만 → user_tags fallback, isPersonalized=true")
    void getRecommendContents_historyTagsInsufficient_fallsBackToUserTags() throws JsonProcessingException {
        List<UUID> historyTagIds = List.of(UUID.randomUUID());
        List<Content> fewContents = tenContents.subList(0, 5);

        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(historyTagIds);
        given(objectMapper.writeValueAsString(any())).willReturn("[\"uuid\"]");

        Tag histTag = Tag.builder().name("History").build();
        given(tagRepository.findAllById(any())).willReturn(List.of(histTag));
        given(contentRepository.findByTagNameInTitleExcludingYoutubeAndScrapped(eq("History"), eq(userId), any()))
                .willReturn(fewContents);

        UUID userTagId = UUID.randomUUID();
        Tag springTag = Tag.builder().name("Spring").build();
        ReflectionTestUtils.setField(springTag, "id", userTagId);
        UserTag userTag = UserTag.builder().tag(springTag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        given(contentRepository.findByTagNameInTitleExcludingYoutubeAndScrapped(eq("Spring"), eq(userId), any()))
                .willReturn(tenContents);

        RecommendContentsResponse result = recommendService.getRecommendContents(userId);

        assertThat(result.isPersonalized()).isTrue();
        assertThat(result.contents()).isNotEmpty();
        verify(userTagRepository).findByUser_Id(userId);
    }

    @Test
    @DisplayName("history 태그 없고 user_tags도 없으면 → 최신순 fallback, isPersonalized=false, message 포함")
    void getRecommendContents_noTags_fallsBackToLatest() throws JsonProcessingException {
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        given(objectMapper.writeValueAsString(any())).willReturn("[]");
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of());
        given(contentRepository.findLatestExcludingYoutubeAndScrapped(eq(userId), any()))
                .willReturn(tenContents);

        RecommendContentsResponse result = recommendService.getRecommendContents(userId);

        assertThat(result.isPersonalized()).isFalse();
        assertThat(result.message()).isEqualTo(RecommendService.NOT_ENOUGH_MESSAGE);
        verify(contentRepository).findLatestExcludingYoutubeAndScrapped(eq(userId), any());
    }

    @Test
    @DisplayName("Redis 캐시 히트 시 history 쿼리 미실행")
    void getOrCacheTagIds_cacheHit_skipsHistoryQuery() throws JsonProcessingException {
        List<UUID> cachedTagIds = List.of(UUID.randomUUID());
        given(valueOps.get(anyString())).willReturn("[\"uuid\"]");
        given(objectMapper.readValue(anyString(), any(TypeReference.class))).willReturn(cachedTagIds);

        List<UUID> result = recommendService.getOrCacheTagIds(userId);

        assertThat(result).isEqualTo(cachedTagIds);
        verify(historyRepository, never()).findDistinctTagIdsByUserActionsAfter(any(), any(), any());
    }

    @Test
    @DisplayName("1개월 이력 없으면 3개월로 재조회")
    void getOrCacheTagIds_emptyOneMonth_expandsToThreeMonths() throws JsonProcessingException {
        UUID tagId = UUID.randomUUID();
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of())
                .willReturn(List.of(tagId));
        given(objectMapper.writeValueAsString(any())).willReturn("[\"uuid\"]");

        List<UUID> result = recommendService.getOrCacheTagIds(userId);

        assertThat(result).containsExactly(tagId);
        verify(historyRepository, times(2))
                .findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any());
    }

    @Test
    @DisplayName("같은 userId + 같은 날짜 시드 → 동일한 셔플 결과")
    void shuffleAndTake_sameSeed_sameShuffle() {
        List<Content> result1 = recommendService.shuffleAndTake(tenContents, userId);
        List<Content> result2 = recommendService.shuffleAndTake(tenContents, userId);

        assertThat(result1).isEqualTo(result2);
    }

    @Test
    @DisplayName("shuffleAndTake — 후보 10개 미만이면 전부 반환")
    void shuffleAndTake_fewerThanTen_returnsAll() {
        List<Content> few = tenContents.subList(0, 4);
        List<Content> result = recommendService.shuffleAndTake(few, userId);
        assertThat(result).hasSize(4);
    }

    @Test
    @DisplayName("shuffleAndTake — 빈 목록이면 빈 리스트 반환")
    void shuffleAndTake_emptyList_returnsEmpty() {
        List<Content> result = recommendService.shuffleAndTake(List.of(), userId);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("history 태그 없고 user_tags 있으면 → user_tags 기반 개인화, isPersonalized=true")
    void getRecommendContents_noHistoryTags_userTagsFallback() throws JsonProcessingException {
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        UUID userTagId = UUID.randomUUID();
        Tag tag = Tag.builder().name("Kotlin").build();
        ReflectionTestUtils.setField(tag, "id", userTagId);
        UserTag userTag = UserTag.builder().tag(tag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        given(contentRepository.findByTagNameInTitleExcludingYoutubeAndScrapped(eq("Kotlin"), eq(userId), any()))
                .willReturn(tenContents);

        RecommendContentsResponse result = recommendService.getRecommendContents(userId);

        assertThat(result.isPersonalized()).isTrue();
        assertThat(result.message()).isNull();
        assertThat(result.contents()).isNotEmpty();
        verify(contentRepository, never()).findLatestExcludingYoutubeAndScrapped(any(), any());
    }

    @Test
    @DisplayName("history 태그 없고 user_tags 있지만 후보 없으면 → latest fallback, isPersonalized=false")
    void getRecommendContents_userTagsButNoCandidates_fallsBackToLatest() throws JsonProcessingException {
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        UUID userTagId = UUID.randomUUID();
        Tag tag = Tag.builder().name("Kotlin").build();
        ReflectionTestUtils.setField(tag, "id", userTagId);
        UserTag userTag = UserTag.builder().tag(tag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        given(contentRepository.findByTagNameInTitleExcludingYoutubeAndScrapped(eq("Kotlin"), eq(userId), any()))
                .willReturn(List.of());
        given(contentRepository.findLatestExcludingYoutubeAndScrapped(eq(userId), any()))
                .willReturn(tenContents);

        RecommendContentsResponse result = recommendService.getRecommendContents(userId);

        assertThat(result.isPersonalized()).isFalse();
        assertThat(result.message()).isEqualTo(RecommendService.NOT_ENOUGH_MESSAGE);
        verify(contentRepository).findLatestExcludingYoutubeAndScrapped(eq(userId), any());
    }

    @Test
    @DisplayName("Redis 역직렬화 실패 시 history 쿼리 실행")
    void getOrCacheTagIds_deserializeFails_fallsBackToHistoryQuery() throws JsonProcessingException {
        UUID tagId = UUID.randomUUID();
        given(valueOps.get(anyString())).willReturn("[invalid-json]");
        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willThrow(new com.fasterxml.jackson.core.JsonParseException(null, "parse error"));
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of(tagId));
        given(objectMapper.writeValueAsString(any())).willReturn("[\"uuid\"]");

        List<UUID> result = recommendService.getOrCacheTagIds(userId);

        assertThat(result).containsExactly(tagId);
        verify(historyRepository).findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any());
    }

    @Test
    @DisplayName("Redis 직렬화 실패해도 태그 목록 정상 반환")
    void getOrCacheTagIds_serializeFails_stillReturnsTagIds() throws JsonProcessingException {
        UUID tagId = UUID.randomUUID();
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of(tagId));
        given(objectMapper.writeValueAsString(any()))
                .willThrow(new com.fasterxml.jackson.core.JsonProcessingException("serialize error") {});

        List<UUID> result = recommendService.getOrCacheTagIds(userId);

        assertThat(result).containsExactly(tagId);
    }

    @Test
    @DisplayName("결과가 최대 8개")
    void getRecommendContents_returnsAtMostEight() throws JsonProcessingException {
        List<Content> lotsOfContents = new ArrayList<>(tenContents);
        ContentSource source = ContentSource.builder()
                .name("Velog").url("https://velog.io").collectMethod("graphql").build();
        for (int i = 10; i < 50; i++) {
            Content c = Content.builder()
                    .source(source).title("글 " + i).author("a")
                    .canonicalUrl("https://velog.io/" + i)
                    .preview("p").publishedAt(LocalDateTime.now().minusDays(i)).build();
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            lotsOfContents.add(c);
        }
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of(UUID.randomUUID()));
        given(objectMapper.writeValueAsString(any())).willReturn("[\"uuid\"]");
        Tag tag = Tag.builder().name("Java").build();
        given(tagRepository.findAllById(any())).willReturn(List.of(tag));
        given(contentRepository.findByTagNameInTitleExcludingYoutubeAndScrapped(anyString(), eq(userId), any()))
                .willReturn(lotsOfContents);

        RecommendContentsResponse result = recommendService.getRecommendContents(userId);

        assertThat(result.contents()).hasSize(8);
    }

    // ─── YouTube 추천 테스트 (DP-463) ─────────────────────────────────────────

    private List<Content> makeYoutubeContents(int count) {
        ContentSource source = ContentSource.builder()
                .name("YouTube").url("https://youtube.com").collectMethod("api").build();
        List<Content> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Content c = Content.builder()
                    .source(source).title("유튜브 영상 " + i).author("채널")
                    .canonicalUrl("https://youtube.com/v" + i)
                    .extra("{\"channelName\":\"채널" + (i % 3) + "\",\"videoId\":\"v" + i + "\"}")
                    .publishedAt(LocalDateTime.now().minusDays(i)).build();
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            list.add(c);
        }
        return list;
    }

    @Test
    @DisplayName("YouTube - 행동 이력 태그 기반 후보 충분 → isPersonalized=true, 8개 반환")
    void getRecommendYoutube_historyTags_returnsPersonalized() throws JsonProcessingException {
        UUID tagId = UUID.randomUUID();
        given(historyRepository.findTagIdActionCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(Collections.singletonList(new Object[]{tagId, "ai_summary_viewed", 3L}));
        given(historyRepository.findViewedContentIdsSince(eq(userId), any())).willReturn(Collections.emptyList());
        given(contentRepository.findYoutubeByTagIdsExcludingScrapped(anyList(), eq(userId), any()))
                .willReturn(makeYoutubeContents(12));
        given(contentRepository.findYoutubeByExcludeTagIdsExcludingScrapped(anyList(), eq(userId), any()))
                .willReturn(makeYoutubeContents(4));
        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willAnswer(inv -> {
                    String json = (String) inv.getArgument(0);
                    if (json != null && json.contains("\"채널1\"")) return Map.of("channelName", "채널1", "videoId", "v1");
                    if (json != null && json.contains("\"채널2\"")) return Map.of("channelName", "채널2", "videoId", "v2");
                    return Map.of("channelName", "채널0", "videoId", "v0");
                });

        YoutubeRecommendResponse result = recommendService.getRecommendYoutube(userId);

        assertThat(result.isPersonalized()).isTrue();
        assertThat(result.message()).isNull();
        assertThat(result.videos()).hasSize(8);
        verify(userTagRepository, never()).findByUser_Id(any());
    }

    @Test
    @DisplayName("YouTube - 행동 이력 없으면 user_tags로 fallback")
    void getRecommendYoutube_noHistory_userTagsFallback() throws JsonProcessingException {
        given(historyRepository.findTagIdActionCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        given(historyRepository.findViewedContentIdsSince(eq(userId), any())).willReturn(List.of());

        UUID tagId = UUID.randomUUID();
        Tag tag = Tag.builder().name("Java").build();
        ReflectionTestUtils.setField(tag, "id", tagId);
        UserTag userTag = UserTag.builder().tag(tag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));

        given(contentRepository.findYoutubeByTagIdsExcludingScrapped(anyList(), eq(userId), any()))
                .willReturn(makeYoutubeContents(8));
        given(contentRepository.findYoutubeByExcludeTagIdsExcludingScrapped(anyList(), eq(userId), any()))
                .willReturn(List.of());
        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willReturn(Map.of("channelName", "채널0", "videoId", "v0"));

        YoutubeRecommendResponse result = recommendService.getRecommendYoutube(userId);

        assertThat(result.videos()).isNotEmpty();
        verify(userTagRepository).findByUser_Id(userId);
    }

    @Test
    @DisplayName("YouTube - 태그 없으면 최신 YouTube cold start, isPersonalized=false")
    void getRecommendYoutube_noTags_coldStart() throws JsonProcessingException {
        given(historyRepository.findTagIdActionCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        given(historyRepository.findViewedContentIdsSince(eq(userId), any())).willReturn(List.of());
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of());
        given(contentRepository.findLatestYoutubeExcludingScrapped(eq(userId), any()))
                .willReturn(makeYoutubeContents(10));
        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willReturn(Map.of("channelName", "채널0", "videoId", "v0"));

        YoutubeRecommendResponse result = recommendService.getRecommendYoutube(userId);

        assertThat(result.isPersonalized()).isFalse();
        assertThat(result.message()).isEqualTo(RecommendService.NOT_ENOUGH_MESSAGE);
        assertThat(result.videos()).hasSize(8);
        verify(contentRepository).findLatestYoutubeExcludingScrapped(eq(userId), any());
    }

    @Test
    @DisplayName("YouTube - 시청 이력에 있는 영상은 결과에서 제외")
    void getRecommendYoutube_viewedContentsExcluded() throws JsonProcessingException {
        UUID tagId = UUID.randomUUID();
        given(historyRepository.findTagIdActionCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(Collections.singletonList(new Object[]{tagId, "scrapped", 2L}));

        List<Content> candidates = makeYoutubeContents(10);
        List<UUID> viewedIds = List.of(candidates.get(0).getId(), candidates.get(1).getId());
        given(historyRepository.findViewedContentIdsSince(eq(userId), any())).willReturn(viewedIds);
        given(contentRepository.findYoutubeByTagIdsExcludingScrapped(anyList(), eq(userId), any()))
                .willReturn(candidates);
        given(contentRepository.findYoutubeByExcludeTagIdsExcludingScrapped(anyList(), eq(userId), any()))
                .willReturn(List.of());
        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willReturn(Map.of("channelName", "채널0", "videoId", "v0"));

        YoutubeRecommendResponse result = recommendService.getRecommendYoutube(userId);

        List<UUID> resultIds = result.videos().stream().map(YoutubeRecommendItem::contentId).toList();
        assertThat(resultIds).isNotEmpty().doesNotContain(viewedIds.get(0), viewedIds.get(1));
    }

    @Test
    @DisplayName("YouTube - 후보 부족 시 최신 YouTube로 채워서 8개 반환")
    void getRecommendYoutube_insufficientCandidates_filledWithLatest() throws JsonProcessingException {
        UUID tagId = UUID.randomUUID();
        given(historyRepository.findTagIdActionCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(Collections.singletonList(new Object[]{tagId, "content_opened", 1L}));
        given(historyRepository.findViewedContentIdsSince(eq(userId), any())).willReturn(Collections.emptyList());

        given(contentRepository.findYoutubeByTagIdsExcludingScrapped(anyList(), eq(userId), any()))
                .willReturn(makeYoutubeContents(3));
        given(contentRepository.findYoutubeByExcludeTagIdsExcludingScrapped(anyList(), eq(userId), any()))
                .willReturn(List.of());
        given(contentRepository.findLatestYoutubeExcludingScrapped(eq(userId), any()))
                .willReturn(makeYoutubeContents(10));
        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willReturn(Map.of("channelName", "채널0", "videoId", "v0"));

        YoutubeRecommendResponse result = recommendService.getRecommendYoutube(userId);

        assertThat(result.videos()).hasSize(8);
        assertThat(result.isPersonalized()).isTrue();
    }

    @Test
    @DisplayName("YouTube - 1개월 이력 없으면 3개월로 재조회")
    void getRecommendYoutube_emptyOneMonth_expandsToThreeMonths() throws JsonProcessingException {
        UUID tagId = UUID.randomUUID();
        given(historyRepository.findTagIdActionCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(Collections.emptyList())
                .willReturn(Collections.singletonList(new Object[]{tagId, "scrapped", 1L}));
        given(historyRepository.findViewedContentIdsSince(eq(userId), any())).willReturn(Collections.emptyList());
        given(contentRepository.findYoutubeByTagIdsExcludingScrapped(anyList(), eq(userId), any()))
                .willReturn(makeYoutubeContents(8));
        given(contentRepository.findYoutubeByExcludeTagIdsExcludingScrapped(anyList(), eq(userId), any()))
                .willReturn(List.of());
        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willReturn(Map.of("channelName", "채널0", "videoId", "v0"));

        recommendService.getRecommendYoutube(userId);

        verify(historyRepository, times(2))
                .findTagIdActionCountsByUserActionsAfter(eq(userId), anyList(), any());
    }

    // ─── 신규 헬퍼 메서드 단위 테스트 ─────────────────────────────────────────

    @Test
    @DisplayName("buildWeightedTagScores - 액션별 가중치 누적 합산")
    void buildWeightedTagScores_multipleActions_accumulated() {
        UUID tagId = UUID.randomUUID();
        given(historyRepository.findTagIdActionCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of(
                        new Object[]{tagId, "ai_quiz_completed", 2L},
                        new Object[]{tagId, "scrapped", 1L}
                ));

        Map<UUID, Double> scores = recommendService.buildWeightedTagScores(userId);

        // 퀴즈완료(5.0×2) + 스크랩(4.0×1) = 14.0
        assertThat(scores).containsEntry(tagId, 14.0);
    }

    @Test
    @DisplayName("buildWeightedTagScores - 1개월 이력 없으면 3개월로 확장")
    void buildWeightedTagScores_emptyOneMonth_expandsToThreeMonths() {
        given(historyRepository.findTagIdActionCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(Collections.emptyList())
                .willReturn(Collections.singletonList(new Object[]{UUID.randomUUID(), "scrapped", 1L}));

        recommendService.buildWeightedTagScores(userId);

        verify(historyRepository, times(2))
                .findTagIdActionCountsByUserActionsAfter(eq(userId), anyList(), any());
    }

    @Test
    @DisplayName("getTopTagIds - 점수 내림차순 상위 N개 반환")
    void getTopTagIds_returnsSortedTopN() {
        UUID id1 = UUID.randomUUID(), id2 = UUID.randomUUID(), id3 = UUID.randomUUID();
        Map<UUID, Double> scores = Map.of(id1, 3.0, id2, 10.0, id3, 5.0);

        List<UUID> top2 = recommendService.getTopTagIds(scores, 2);

        assertThat(top2).containsExactly(id2, id3);
    }

    @Test
    @DisplayName("computeScore - 태그 점수 + 최신성 보너스 합산")
    void computeScore_tagAndRecency() {
        UUID tagId = UUID.randomUUID();
        Tag tag = Tag.builder().name("Java").build();
        ReflectionTestUtils.setField(tag, "id", tagId);

        ContentSource source = ContentSource.builder()
                .name("YouTube").url("https://youtube.com").collectMethod("api").build();
        Content content = Content.builder()
                .source(source).title("Java 강의")
                .canonicalUrl("https://youtube.com/java")
                .publishedAt(LocalDateTime.now().minusDays(10))
                .build();
        ReflectionTestUtils.setField(content, "id", UUID.randomUUID());

        ContentTag ct = ContentTag.builder().content(content).tag(tag).build();
        ReflectionTestUtils.setField(content, "contentTags", List.of(ct));

        Map<UUID, Double> tagScores = Map.of(tagId, 5.0);
        double score = recommendService.computeScore(content, tagScores);

        // tagScore=5.0 + recencyBonus=(30-10)*0.1=2.0 → 7.0
        assertThat(score).isEqualTo(7.0);
    }

    @Test
    @DisplayName("computeScore - 30일 이상 된 영상은 최신성 보너스 0")
    void computeScore_oldContent_noRecencyBonus() {
        UUID tagId = UUID.randomUUID();
        Tag tag = Tag.builder().name("Java").build();
        ReflectionTestUtils.setField(tag, "id", tagId);

        ContentSource source = ContentSource.builder()
                .name("YouTube").url("https://youtube.com").collectMethod("api").build();
        Content content = Content.builder()
                .source(source).title("Java 강의")
                .canonicalUrl("https://youtube.com/old")
                .publishedAt(LocalDateTime.now().minusDays(40))
                .build();
        ReflectionTestUtils.setField(content, "id", UUID.randomUUID());

        ContentTag ct = ContentTag.builder().content(content).tag(tag).build();
        ReflectionTestUtils.setField(content, "contentTags", List.of(ct));

        Map<UUID, Double> tagScores = Map.of(tagId, 5.0);
        double score = recommendService.computeScore(content, tagScores);

        assertThat(score).isEqualTo(5.0);
    }

    @Test
    @DisplayName("applyChannelDiversityPenalty - 같은 채널 두 번째 영상은 페널티 적용")
    void applyChannelDiversityPenalty_sameChannel_penaltyApplied() throws JsonProcessingException {
        UUID tagId = UUID.randomUUID();
        Map<UUID, Double> tagScores = Map.of(tagId, 10.0);

        ContentSource source = ContentSource.builder()
                .name("YouTube").url("https://youtube.com").collectMethod("api").build();

        // 같은 채널 "Fireship" 영상 3개 (점수 동일)
        List<Content> candidates = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Content c = Content.builder()
                    .source(source).title("Fireship " + i).author("Fireship")
                    .canonicalUrl("https://youtube.com/fireship/" + i)
                    .extra("{\"channelName\":\"Fireship\"}")
                    .publishedAt(LocalDateTime.now().minusDays(i)).build();
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            candidates.add(c);
        }

        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willReturn(Map.of("channelName", "Fireship"));

        List<Content> ranked = recommendService.applyChannelDiversityPenalty(candidates, tagScores);

        // 채널당 최대 2개 하드캡 — 같은 채널 3개 중 2개만 선택됨
        assertThat(ranked).hasSize(RecommendService.MAX_PER_CHANNEL);
        assertThat(ranked.get(0)).isEqualTo(candidates.get(0));
    }

    @Test
    @DisplayName("extractChannel - extra가 null이면 contentId 반환")
    void extractChannel_nullExtra_returnsContentId() {
        ContentSource source = ContentSource.builder()
                .name("YouTube").url("https://youtube.com").collectMethod("api").build();
        Content c = Content.builder().source(source).title("영상").canonicalUrl("https://youtube.com/v1").build();
        ReflectionTestUtils.setField(c, "id", UUID.randomUUID());

        assertThat(recommendService.extractChannel(c)).isEqualTo(c.getId().toString());
    }

    @Test
    @DisplayName("extractChannel - channelName 키 없으면 contentId 반환")
    void extractChannel_noChannelName_returnsContentId() throws JsonProcessingException {
        ContentSource source = ContentSource.builder()
                .name("YouTube").url("https://youtube.com").collectMethod("api").build();
        Content c = Content.builder().source(source).title("영상").canonicalUrl("https://youtube.com/v2")
                .extra("{\"videoId\":\"abc\"}").build();
        ReflectionTestUtils.setField(c, "id", UUID.randomUUID());

        given(objectMapper.readValue(anyString(), any(TypeReference.class))).willReturn(Map.of("videoId", "abc"));

        assertThat(recommendService.extractChannel(c)).isEqualTo(c.getId().toString());
    }

    @Test
    @DisplayName("extractChannel - JSON 파싱 실패 시 contentId 반환")
    void extractChannel_parseError_returnsContentId() throws JsonProcessingException {
        ContentSource source = ContentSource.builder()
                .name("YouTube").url("https://youtube.com").collectMethod("api").build();
        Content c = Content.builder().source(source).title("영상").canonicalUrl("https://youtube.com/v3")
                .extra("{invalid}").build();
        ReflectionTestUtils.setField(c, "id", UUID.randomUUID());

        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willThrow(new com.fasterxml.jackson.core.JsonParseException(null, "error"));

        assertThat(recommendService.extractChannel(c)).isEqualTo(c.getId().toString());
    }

    @Test
    @DisplayName("YouTube - extra JSON 파싱 실패 시 빈 맵으로 대체, 결과 정상 반환")
    void getRecommendYoutube_extraParseError_returnsEmptyExtra() throws JsonProcessingException {
        UUID tagId = UUID.randomUUID();
        given(historyRepository.findTagIdActionCountsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(Collections.singletonList(new Object[]{tagId, "scrapped", 1L}));
        given(historyRepository.findViewedContentIdsSince(eq(userId), any())).willReturn(Collections.emptyList());

        List<Content> videos = makeYoutubeContents(1);
        given(contentRepository.findYoutubeByTagIdsExcludingScrapped(anyList(), eq(userId), any()))
                .willReturn(videos);
        given(contentRepository.findYoutubeByExcludeTagIdsExcludingScrapped(anyList(), eq(userId), any()))
                .willReturn(Collections.emptyList());
        given(contentRepository.findLatestYoutubeExcludingScrapped(eq(userId), any()))
                .willReturn(Collections.emptyList());

        // extractChannel 호출(applyChannelDiversityPenalty)은 성공, parseExtra(buildYoutubeResponse)는 실패
        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willReturn(Map.of("channelName", "채널0"))
                .willThrow(new com.fasterxml.jackson.core.JsonParseException(null, "error"));

        YoutubeRecommendResponse result = recommendService.getRecommendYoutube(userId);

        assertThat(result.videos()).hasSize(1);
    }
}
