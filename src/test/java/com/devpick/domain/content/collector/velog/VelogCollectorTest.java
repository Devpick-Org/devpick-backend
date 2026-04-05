package com.devpick.domain.content.collector.velog;

import com.devpick.domain.content.collector.CollectedContent;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.entity.ContentTag;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
import com.devpick.domain.content.repository.ContentTagRepository;
import com.devpick.domain.user.entity.Tag;
import com.devpick.domain.user.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VelogCollectorTest {

    @InjectMocks
    private VelogCollector collector;

    @Mock
    private WebClient webClient;
    @Mock
    private ContentRepository contentRepository;
    @Mock
    private ContentSourceRepository contentSourceRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private ContentTagRepository contentTagRepository;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ContentSource velogSource;

    @BeforeEach
    void setUp() {
        velogSource = ContentSource.builder()
                .name("Velog")
                .url("https://v2.velog.io/graphql")
                .collectMethod("graphql")
                .build();
    }

    // ─── sourceName ──────────────────────────────────────────────

    @Test
    @DisplayName("sourceName — 'Velog' 반환")
    void sourceName_returnsVelog() {
        assertThat(collector.sourceName()).isEqualTo("Velog");
    }

    // ─── collect (ContentCollector 공통 흐름) ─────────────────────

    @Test
    @DisplayName("collect — ContentSource 없으면 0 반환하고 저장 안 함")
    void collect_noSource_returnsZero() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Velog"))
                .willReturn(Optional.empty());

        int result = collector.collect("spring");

        assertThat(result).isZero();
        verifyNoInteractions(contentRepository);
    }

    @Test
    @DisplayName("collect — 정상 수집 후 저장 수 반환")
    void collect_success_returnsSavedCount() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Velog"))
                .willReturn(Optional.of(velogSource));

        VelogCollector spyCollector = spy(collector);
        CollectedContent item1 = buildCollectedContent("https://velog.io/@user/post-1");
        CollectedContent item2 = buildCollectedContent("https://velog.io/@user/post-2");
        doReturn(List.of(item1, item2)).when(spyCollector).fetchItems(anyString());
        given(contentRepository.save(any(Content.class))).willAnswer(inv -> inv.getArgument(0));

        int result = spyCollector.collect("spring");

        assertThat(result).isEqualTo(2);
        verify(contentRepository, times(2)).save(any(Content.class));
    }

    @Test
    @DisplayName("collect — 중복 URL은 저장 안 하고 카운트에서 제외")
    void collect_duplicateUrl_skippedSilently() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Velog"))
                .willReturn(Optional.of(velogSource));

        VelogCollector spyCollector = spy(collector);
        CollectedContent item = buildCollectedContent("https://velog.io/@user/post-1");
        doReturn(List.of(item)).when(spyCollector).fetchItems(anyString());
        given(contentRepository.save(any(Content.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        int result = spyCollector.collect("spring");

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("collect — 저장 중 예외 발생해도 나머지 계속 처리")
    void collect_saveException_continuesProcessing() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Velog"))
                .willReturn(Optional.of(velogSource));

        VelogCollector spyCollector = spy(collector);
        CollectedContent item1 = buildCollectedContent("https://velog.io/@user/post-1");
        CollectedContent item2 = buildCollectedContent("https://velog.io/@user/post-2");
        doReturn(List.of(item1, item2)).when(spyCollector).fetchItems(anyString());

        given(contentRepository.save(any(Content.class)))
                .willThrow(new RuntimeException("DB error"))
                .willAnswer(inv -> inv.getArgument(0));

        int result = spyCollector.collect("spring");

        assertThat(result).isEqualTo(1);
    }

    // ─── fetchPosts ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("fetchPosts — 정상 응답 시 CollectedContent 리스트 반환")
    void fetchPosts_success_returnsCollectedContents() {
        VelogPost post = buildVelogPost("spring-boot-tips", "Spring Boot 팁");
        VelogGraphQlResponse response = new VelogGraphQlResponse(
                new VelogGraphQlResponse.Data(List.of(post))
        );

        mockWebClientPost(Mono.just(response));

        List<CollectedContent> result = collector.fetchPosts();

        assertThat(result).hasSize(1);
        CollectedContent content = result.get(0);
        assertThat(content.title()).isEqualTo("Spring Boot 팁");
        assertThat(content.author()).isEqualTo("devuser");
        assertThat(content.canonicalUrl()).isEqualTo("https://velog.io/@devuser/spring-boot-tips");
        assertThat(content.isOriginalVisible()).isFalse();
        assertThat(content.originalContent()).isNull();
        assertThat(content.licenseType()).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("fetchPosts — null 응답 시 빈 리스트 반환")
    void fetchPosts_nullResponse_returnsEmpty() {
        mockWebClientPost(Mono.empty());
        assertThat(collector.fetchPosts()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("fetchPosts — WebClientResponseException 발생 시 빈 리스트 반환")
    void fetchPosts_httpError_returnsEmpty() {
        mockWebClientPost(Mono.error(
                WebClientResponseException.create(500, "Internal Server Error", null, null, null)
        ));
        assertThat(collector.fetchPosts()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("fetchPosts — 일반 예외 발생 시 빈 리스트 반환")
    void fetchPosts_exception_returnsEmpty() {
        mockWebClientPost(Mono.error(new RuntimeException("connection timeout")));
        assertThat(collector.fetchPosts()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("fetchPosts — user null이면 author 'Unknown' 처리")
    void fetchPosts_nullUser_usesUnknownAuthor() {
        VelogPost post = new VelogPost("id-1", "제목", "설명", "slug", "2024-01-01T00:00:00Z", List.of("java"), null);
        mockWebClientPost(Mono.just(new VelogGraphQlResponse(new VelogGraphQlResponse.Data(List.of(post)))));

        List<CollectedContent> result = collector.fetchPosts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).author()).isEqualTo("Unknown");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("fetchPosts — user.username null이면 author 'Unknown' 처리")
    void fetchPosts_nullUsername_usesUnknownAuthor() {
        VelogPost post = new VelogPost("id-2", "제목", "설명", "slug", "2024-01-01T00:00:00Z", List.of(), new VelogPost.VelogUser(null));
        mockWebClientPost(Mono.just(new VelogGraphQlResponse(new VelogGraphQlResponse.Data(List.of(post)))));

        List<CollectedContent> result = collector.fetchPosts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).author()).isEqualTo("Unknown");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("fetchPosts — urlSlug null이면 canonicalUrl null")
    void fetchPosts_nullUrlSlug_canonicalUrlNull() {
        VelogPost post = new VelogPost("id-3", "제목", "설명", null, "2024-01-01T00:00:00Z", List.of(), new VelogPost.VelogUser("devuser"));
        mockWebClientPost(Mono.just(new VelogGraphQlResponse(new VelogGraphQlResponse.Data(List.of(post)))));

        assertThat(collector.fetchPosts().get(0).canonicalUrl()).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("fetchPosts — releasedAt null이면 현재 시각으로 fallback")
    void fetchPosts_nullReleasedAt_usesNow() {
        VelogPost post = new VelogPost("id-4", "제목", "설명", "slug", null, List.of(), new VelogPost.VelogUser("devuser"));
        mockWebClientPost(Mono.just(new VelogGraphQlResponse(new VelogGraphQlResponse.Data(List.of(post)))));

        assertThat(collector.fetchPosts().get(0).publishedAt()).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("fetchPosts — releasedAt 파싱 실패 시 현재 시각으로 fallback")
    void fetchPosts_invalidReleasedAt_usesNow() {
        VelogPost post = new VelogPost("id-5", "제목", "설명", "slug", "NOT_A_DATE", List.of(), new VelogPost.VelogUser("devuser"));
        mockWebClientPost(Mono.just(new VelogGraphQlResponse(new VelogGraphQlResponse.Data(List.of(post)))));

        assertThat(collector.fetchPosts().get(0).publishedAt()).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("fetchPosts — tags null이면 빈 리스트로 처리")
    void fetchPosts_nullTags_usesEmptyList() {
        VelogPost post = new VelogPost("id-6", "제목", "설명", "slug", "2024-01-01T00:00:00Z", null, new VelogPost.VelogUser("devuser"));
        mockWebClientPost(Mono.just(new VelogGraphQlResponse(new VelogGraphQlResponse.Data(List.of(post)))));

        assertThat(collector.fetchPosts().get(0).tags()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("fetchPosts — Origin 헤더 'https://velog.io' 포함하여 요청")
    void fetchPosts_includesOriginHeader() {
        VelogGraphQlResponse response = new VelogGraphQlResponse(new VelogGraphQlResponse.Data(List.of()));
        mockWebClientPost(Mono.just(response));

        collector.fetchPosts();

        verify(requestBodySpec).header("Origin", "https://velog.io");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("collect — save 시 isOriginalVisible=false, licenseType=null 매핑 확인 (ADR-006)")
    void collect_contentMapping_summaryOnlyPolicy() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Velog"))
                .willReturn(Optional.of(velogSource));

        VelogCollector spyCollector = spy(collector);
        CollectedContent item = buildCollectedContent("https://velog.io/@user/post-1");
        doReturn(List.of(item)).when(spyCollector).fetchItems(anyString());

        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        given(contentRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

        spyCollector.collect("spring");

        Content saved = captor.getValue();
        assertThat(saved.getIsOriginalVisible()).isFalse();
        assertThat(saved.getLicenseType()).isNull();
        assertThat(saved.getSource()).isEqualTo(velogSource);
    }

    // ─── afterSave (tag 저장 로직) ────────────────────────────────

    @Test
    @DisplayName("afterSave — DB에 있는 태그는 content_tags에 저장됨")
    void afterSave_matchingTags_savesContentTags() {
        Content content = Content.builder()
                .source(velogSource).title("제목").canonicalUrl("https://velog.io/@u/slug").build();
        Tag javaTag = Tag.builder().name("Java").build();
        given(tagRepository.findByNameIn(List.of("Java"))).willReturn(List.of(javaTag));
        given(contentTagRepository.save(any(ContentTag.class))).willAnswer(inv -> inv.getArgument(0));

        CollectedContent item = buildCollectedContent("https://velog.io/@u/slug", List.of("Java"));
        collector.afterSave(content, item);

        verify(contentTagRepository, times(1)).save(any(ContentTag.class));
    }

    @Test
    @DisplayName("afterSave — DB에 없는 태그는 content_tags에 저장하지 않음 (skip 전략)")
    void afterSave_noMatchingTags_skipsTagSave() {
        Content content = Content.builder()
                .source(velogSource).title("제목").canonicalUrl("https://velog.io/@u/slug").build();
        given(tagRepository.findByNameIn(anyList())).willReturn(List.of());

        CollectedContent item = buildCollectedContent("https://velog.io/@u/slug", List.of("알수없는태그"));
        collector.afterSave(content, item);

        verifyNoInteractions(contentTagRepository);
    }

    @Test
    @DisplayName("afterSave — 태그 리스트가 비어있으면 DB 조회 자체를 하지 않음")
    void afterSave_emptyTagList_skipsDbLookup() {
        Content content = Content.builder()
                .source(velogSource).title("제목").canonicalUrl("https://velog.io/@u/slug").build();

        CollectedContent item = buildCollectedContent("https://velog.io/@u/slug", List.of());
        collector.afterSave(content, item);

        verifyNoInteractions(tagRepository);
        verifyNoInteractions(contentTagRepository);
    }

    @Test
    @DisplayName("afterSave — tag 저장 중 예외 발생해도 collect 흐름에 영향 없음")
    void afterSave_tagSaveException_doesNotBreakCollect() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Velog"))
                .willReturn(Optional.of(velogSource));

        VelogCollector spyCollector = spy(collector);
        CollectedContent item = buildCollectedContent("https://velog.io/@u/slug", List.of("Java"));
        doReturn(List.of(item)).when(spyCollector).fetchItems(anyString());

        given(contentRepository.save(any(Content.class))).willAnswer(inv -> inv.getArgument(0));
        given(tagRepository.findByNameIn(anyList())).willThrow(new RuntimeException("DB down"));

        int result = spyCollector.collect("spring");

        assertThat(result).isEqualTo(1);
    }

    // ─── helpers ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void mockWebClientPost(Mono<?> mono) {
        given(webClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.contentType(any())).willReturn(requestBodySpec);
        given(requestBodySpec.header(anyString(), anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.bodyValue(any())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(VelogGraphQlResponse.class))
                .willReturn((Mono<VelogGraphQlResponse>) mono);
    }

    private VelogPost buildVelogPost(String urlSlug, String title) {
        return new VelogPost(
                "post-id-abc", title, "짧은 설명입니다.", urlSlug,
                "2024-05-01T12:00:00Z", List.of("java", "spring"),
                new VelogPost.VelogUser("devuser")
        );
    }

    private CollectedContent buildCollectedContent(String url) {
        return buildCollectedContent(url, List.of("java"));
    }

    private CollectedContent buildCollectedContent(String url, List<String> tags) {
        return CollectedContent.of(
                "테스트 게시글 제목", "devuser", url, "짧은 설명",
                null, false, null, java.time.LocalDateTime.now(), tags
        );
    }
}
