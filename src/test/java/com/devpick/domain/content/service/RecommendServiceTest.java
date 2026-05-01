package com.devpick.domain.content.service;

import com.devpick.domain.content.dto.RecommendContentsResponse;
import com.devpick.domain.content.dto.YoutubeRecommendResponse;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.LikeRepository;
import com.devpick.domain.report.repository.HistoryRepository;
import com.devpick.domain.user.entity.UserTag;
import com.devpick.domain.user.entity.Tag;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendServiceTest {

    @InjectMocks private RecommendService recommendService;

    @Mock private HistoryRepository historyRepository;
    @Mock private ContentRepository contentRepository;
    @Mock private UserTagRepository userTagRepository;
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

    @Test
    @DisplayName("history 태그 기반 후보 10개 이상 → 개인화 응답, isPersonalized=true")
    void getRecommendContents_historyTags_returnsPersonalized() throws JsonProcessingException {
        List<UUID> tagIds = List.of(UUID.randomUUID());
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagIds);
        given(objectMapper.writeValueAsString(any())).willReturn("[\"uuid\"]");
        given(contentRepository.findRecommendCandidatesByTags(eq(tagIds), eq(userId), any()))
                .willReturn(tenContents);

        RecommendContentsResponse result = recommendService.getRecommendContents(userId);

        assertThat(result.isPersonalized()).isTrue();
        assertThat(result.message()).isNull();
        assertThat(result.contents()).hasSize(10);
        verify(userTagRepository, never()).findByUser_Id(any());
    }

    @Test
    @DisplayName("history 태그 기반 후보 10개 미만 → user_tags fallback, isPersonalized=true")
    void getRecommendContents_historyTagsInsufficient_fallsBackToUserTags() throws JsonProcessingException {
        UUID tagId = UUID.randomUUID();
        List<UUID> historyTagIds = List.of(tagId);
        List<Content> fewContents = tenContents.subList(0, 5);

        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(historyTagIds);
        given(objectMapper.writeValueAsString(any())).willReturn("[\"uuid\"]");
        given(contentRepository.findRecommendCandidatesByTags(eq(historyTagIds), eq(userId), any()))
                .willReturn(fewContents);

        UUID userTagId = UUID.randomUUID();
        Tag tag = Tag.builder().name("Spring").build();
        ReflectionTestUtils.setField(tag, "id", userTagId);
        UserTag userTag = UserTag.builder().tag(tag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));

        List<UUID> userTagIds = List.of(userTagId);
        given(contentRepository.findRecommendCandidatesByTags(eq(userTagIds), eq(userId), any()))
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
        verify(historyRepository, org.mockito.Mockito.times(2))
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
        given(contentRepository.findRecommendCandidatesByTags(eq(List.of(userTagId)), eq(userId), any()))
                .willReturn(tenContents);

        RecommendContentsResponse result = recommendService.getRecommendContents(userId);

        assertThat(result.isPersonalized()).isTrue();
        assertThat(result.message()).isNull();
        assertThat(result.contents()).isNotEmpty();
        verify(contentRepository, never()).findLatestExcludingYoutubeAndScrapped(any(), any());
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

    // ─── YouTube 추천 테스트 ───────────────────────────────────────────────────

    @Test
    @DisplayName("YouTube - history 태그 기반 후보 10개 이상 → 개인화 응답")
    void getRecommendYoutube_historyTags_returnsPersonalized() throws JsonProcessingException {
        List<UUID> tagIds = List.of(UUID.randomUUID());
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagIds);
        given(objectMapper.writeValueAsString(any())).willReturn("[\"uuid\"]");
        given(contentRepository.findYoutubeRecommendCandidatesByTags(eq(tagIds), eq(userId), any()))
                .willReturn(tenContents);

        YoutubeRecommendResponse result = recommendService.getRecommendYoutube(userId);

        assertThat(result.isPersonalized()).isTrue();
        assertThat(result.message()).isNull();
        assertThat(result.videos()).hasSize(10);
        verify(userTagRepository, never()).findByUser_Id(any());
    }

    @Test
    @DisplayName("YouTube - history 태그 기반 후보 10개 미만 → user_tags fallback")
    void getRecommendYoutube_historyTagsInsufficient_fallsBackToUserTags() throws JsonProcessingException {
        List<UUID> historyTagIds = List.of(UUID.randomUUID());
        List<Content> fewContents = tenContents.subList(0, 5);

        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(historyTagIds);
        given(objectMapper.writeValueAsString(any())).willReturn("[\"uuid\"]");
        given(contentRepository.findYoutubeRecommendCandidatesByTags(eq(historyTagIds), eq(userId), any()))
                .willReturn(fewContents);

        UUID userTagId = UUID.randomUUID();
        Tag tag = Tag.builder().name("Spring").build();
        ReflectionTestUtils.setField(tag, "id", userTagId);
        UserTag userTag = UserTag.builder().tag(tag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        given(contentRepository.findYoutubeRecommendCandidatesByTags(eq(List.of(userTagId)), eq(userId), any()))
                .willReturn(tenContents);

        YoutubeRecommendResponse result = recommendService.getRecommendYoutube(userId);

        assertThat(result.isPersonalized()).isTrue();
        assertThat(result.videos()).isNotEmpty();
        verify(userTagRepository).findByUser_Id(userId);
    }

    @Test
    @DisplayName("YouTube - history 태그 없고 user_tags도 없으면 → 빈 배열 반환, isPersonalized=false")
    void getRecommendYoutube_noTags_returnsEmpty() throws JsonProcessingException {
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        given(objectMapper.writeValueAsString(any())).willReturn("[]");
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of());

        YoutubeRecommendResponse result = recommendService.getRecommendYoutube(userId);

        assertThat(result.videos()).isEmpty();
        assertThat(result.isPersonalized()).isFalse();
        assertThat(result.message()).isEqualTo(RecommendService.NOT_ENOUGH_MESSAGE);
        verify(contentRepository, never()).findLatestYoutubeExcludingScrapped(any(), any());
    }

    @Test
    @DisplayName("YouTube - history 태그 없고 user_tags 있으면 → user_tags 기반 개인화")
    void getRecommendYoutube_noHistoryTags_userTagsFallback() throws JsonProcessingException {
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of());
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        UUID userTagId = UUID.randomUUID();
        Tag tag = Tag.builder().name("Kotlin").build();
        ReflectionTestUtils.setField(tag, "id", userTagId);
        UserTag userTag = UserTag.builder().tag(tag).build();
        given(userTagRepository.findByUser_Id(userId)).willReturn(List.of(userTag));
        given(contentRepository.findYoutubeRecommendCandidatesByTags(eq(List.of(userTagId)), eq(userId), any()))
                .willReturn(tenContents);

        YoutubeRecommendResponse result = recommendService.getRecommendYoutube(userId);

        assertThat(result.isPersonalized()).isTrue();
        assertThat(result.videos()).isNotEmpty();
        verify(contentRepository, never()).findLatestYoutubeExcludingScrapped(any(), any());
    }

    @Test
    @DisplayName("YouTube - extra JSON 파싱 성공 시 videoId/channelName/duration 추출")
    void getRecommendYoutube_extraJsonParsed_fieldsExtracted() throws JsonProcessingException {
        ContentSource source = ContentSource.builder()
                .name("YouTube").url("https://youtube.com").collectMethod("api").build();
        List<Content> youtubeContents = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Content c = Content.builder()
                    .source(source).title("유튜브 영상 " + i).author("채널명")
                    .canonicalUrl("https://youtube.com/watch?v=v" + i)
                    .extra("{\"videoId\":\"abc" + i + "\",\"channelName\":\"테스트채널\",\"duration\":\"PT10M\"}")
                    .publishedAt(java.time.LocalDateTime.now().minusDays(i)).build();
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            youtubeContents.add(c);
        }

        List<UUID> tagIds = List.of(UUID.randomUUID());
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagIds);
        given(objectMapper.writeValueAsString(any())).willReturn("[\"uuid\"]");
        given(contentRepository.findYoutubeRecommendCandidatesByTags(anyList(), eq(userId), any()))
                .willReturn(youtubeContents);
        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willReturn(Map.of("videoId", "abc0", "channelName", "테스트채널", "duration", "PT10M"));

        YoutubeRecommendResponse result = recommendService.getRecommendYoutube(userId);

        assertThat(result.videos()).hasSize(10);
        assertThat(result.videos()).allMatch(v -> v.channelName().equals("테스트채널"));
        assertThat(result.videos()).allMatch(v -> v.duration().equals("PT10M"));
    }

    @Test
    @DisplayName("YouTube - extra JSON 파싱 실패 시 videoId null로 처리")
    void getRecommendYoutube_extraJsonParseFails_fieldsAreNull() throws JsonProcessingException {
        ContentSource source = ContentSource.builder()
                .name("YouTube").url("https://youtube.com").collectMethod("api").build();
        List<Content> youtubeContents = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Content c = Content.builder()
                    .source(source).title("유튜브 영상 " + i).author("채널명")
                    .canonicalUrl("https://youtube.com/watch?v=bad" + i)
                    .extra("{invalid-json}")
                    .publishedAt(java.time.LocalDateTime.now().minusDays(i)).build();
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            youtubeContents.add(c);
        }

        List<UUID> tagIds = List.of(UUID.randomUUID());
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(tagIds);
        given(objectMapper.writeValueAsString(any())).willReturn("[\"uuid\"]");
        given(contentRepository.findYoutubeRecommendCandidatesByTags(anyList(), eq(userId), any()))
                .willReturn(youtubeContents);
        given(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .willThrow(new com.fasterxml.jackson.core.JsonParseException(null, "parse error"));

        YoutubeRecommendResponse result = recommendService.getRecommendYoutube(userId);

        assertThat(result.videos()).hasSize(10);
        assertThat(result.videos()).allMatch(v -> v.videoId() == null);
        assertThat(result.videos()).allMatch(v -> v.channelName() == null);
    }

    @Test
    @DisplayName("YouTube - 결과가 최대 10개")
    void getRecommendYoutube_returnsAtMostTen() throws JsonProcessingException {
        List<Content> lotsOfContents = new ArrayList<>(tenContents);
        ContentSource source = ContentSource.builder()
                .name("YouTube").url("https://youtube.com").collectMethod("api").build();
        for (int i = 10; i < 50; i++) {
            Content c = Content.builder()
                    .source(source).title("유튜브 " + i).author("채널")
                    .canonicalUrl("https://youtube.com/" + i)
                    .publishedAt(java.time.LocalDateTime.now().minusDays(i)).build();
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            lotsOfContents.add(c);
        }
        given(valueOps.get(anyString())).willReturn(null);
        given(historyRepository.findDistinctTagIdsByUserActionsAfter(eq(userId), anyList(), any()))
                .willReturn(List.of(UUID.randomUUID()));
        given(objectMapper.writeValueAsString(any())).willReturn("[\"uuid\"]");
        given(contentRepository.findYoutubeRecommendCandidatesByTags(anyList(), eq(userId), any()))
                .willReturn(lotsOfContents);

        YoutubeRecommendResponse result = recommendService.getRecommendYoutube(userId);

        assertThat(result.videos()).hasSize(10);
    }

    @Test
    @DisplayName("결과가 최대 10개")
    void getRecommendContents_returnsAtMostTen() throws JsonProcessingException {
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
        given(contentRepository.findRecommendCandidatesByTags(anyList(), eq(userId), any()))
                .willReturn(lotsOfContents);

        RecommendContentsResponse result = recommendService.getRecommendContents(userId);

        assertThat(result.contents()).hasSize(10);
    }
}
