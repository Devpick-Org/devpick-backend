package com.devpick.domain.content.collector.stackoverflow;

import com.devpick.domain.content.collector.CollectedContent;
import com.devpick.domain.content.entity.Content;
import com.devpick.domain.content.entity.ContentSource;
import com.devpick.domain.content.repository.ContentRepository;
import com.devpick.domain.content.repository.ContentSourceRepository;
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

import com.devpick.domain.content.dto.StackOverflowAnswerDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StackOverflowCollectorTest {

    @InjectMocks
    private StackOverflowCollector collector;

    @Mock
    private WebClient webClient;
    @Mock
    private ContentRepository contentRepository;
    @Mock
    private ContentSourceRepository contentSourceRepository;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ContentSource stackOverflowSource;

    @BeforeEach
    void setUp() {
        stackOverflowSource = ContentSource.builder()
                .name("Stack Overflow")
                .url("https://api.stackexchange.com")
                .collectMethod("api")
                .build();
    }


    @Test
    @DisplayName("sourceName — 'Stack Overflow' 반환")
    void sourceName_returnsStackOverflow() {
        assertThat(collector.sourceName()).isEqualTo("Stack Overflow");
    }

    @Test
    @DisplayName("collect — ContentSource 없으면 0 반환하고 API 호출 안 함")
    void collect_noSource_returnsZero() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Stack Overflow"))
                .willReturn(Optional.empty());

        int result = collector.collect("java");

        assertThat(result).isZero();
        verifyNoInteractions(contentRepository);
    }

    @Test
    @DisplayName("collect — 정상 수집 후 저장 수 반환")
    void collect_success_returnsSavedCount() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Stack Overflow"))
                .willReturn(Optional.of(stackOverflowSource));

        StackOverflowCollector spyCollector = spy(collector);
        CollectedContent item1 = buildCollectedContent("https://stackoverflow.com/q/1");
        CollectedContent item2 = buildCollectedContent("https://stackoverflow.com/q/2");
        doReturn(List.of(item1, item2)).when(spyCollector).fetchItems(anyString());
        given(contentRepository.save(any(Content.class))).willAnswer(inv -> inv.getArgument(0));

        int result = spyCollector.collect("java");

        assertThat(result).isEqualTo(2);
        verify(contentRepository, times(2)).save(any(Content.class));
    }

    @Test
    @DisplayName("collect — 중복 URL은 저장 안 하고 카운트에서 제외")
    void collect_duplicateUrl_skippedSilently() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Stack Overflow"))
                .willReturn(Optional.of(stackOverflowSource));

        StackOverflowCollector spyCollector = spy(collector);
        CollectedContent item = buildCollectedContent("https://stackoverflow.com/q/1");
        doReturn(List.of(item)).when(spyCollector).fetchItems(anyString());
        given(contentRepository.save(any(Content.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        int result = spyCollector.collect("java");

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("collect — 저장 중 예외 발생해도 나머지 계속 처리")
    void collect_saveException_continuesProcessing() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Stack Overflow"))
                .willReturn(Optional.of(stackOverflowSource));

        StackOverflowCollector spyCollector = spy(collector);
        CollectedContent item1 = buildCollectedContent("https://stackoverflow.com/q/1");
        CollectedContent item2 = buildCollectedContent("https://stackoverflow.com/q/2");
        doReturn(List.of(item1, item2)).when(spyCollector).fetchItems(anyString());

        given(contentRepository.save(any(Content.class)))
                .willThrow(new RuntimeException("DB error"))
                .willAnswer(inv -> inv.getArgument(0));

        int result = spyCollector.collect("java");

        assertThat(result).isEqualTo(1);
        verify(contentRepository, times(2)).save(any(Content.class));
    }

    @Test
    @DisplayName("fetchQuestions — API 정상 응답 시 CollectedContent 리스트 반환")
    void fetchQuestions_success_returnsCollectedContents() {
        StackOverflowQuestion.Owner owner = new StackOverflowQuestion.Owner("John", 123L);
        StackOverflowQuestion question = new StackOverflowQuestion(
                12345L,
                "How to use Spring Boot?",
                "https://stackoverflow.com/questions/12345",
                owner,
                List.of("java", "spring-boot"),
                "This is body markdown",
                1700000000L,
                10,
                500,
                true
        );
        StackOverflowApiResponse apiResponse = new StackOverflowApiResponse(
                List.of(question), false, 9990
        );

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class))
                .willReturn(Mono.just(apiResponse));

        List<CollectedContent> result = collector.fetchQuestions("java;spring-boot");

        assertThat(result).hasSize(1);
        CollectedContent content = result.get(0);
        assertThat(content.title()).isEqualTo("How to use Spring Boot?");
        assertThat(content.author()).isEqualTo("John");
        assertThat(content.canonicalUrl()).isEqualTo("https://stackoverflow.com/questions/12345");
        assertThat(content.isOriginalVisible()).isTrue();
        assertThat(content.licenseType()).isEqualTo("CC BY-SA 4.0");
        assertThat(content.tags()).containsExactly("java", "spring-boot");
    }

    @Test
    @DisplayName("fetchQuestions — API null 응답 시 빈 리스트 반환")
    void fetchQuestions_nullResponse_returnsEmpty() {
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class))
                .willReturn(Mono.empty());

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchQuestions — API 에러 응답 시 빈 리스트 반환")
    void fetchQuestions_apiError_returnsEmpty() {
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class))
                .willReturn(Mono.error(new RuntimeException("connection refused")));

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchQuestions — owner null이면 author Unknown으로 처리")
    void fetchQuestions_nullOwner_usesUnknownAuthor() {
        StackOverflowQuestion question = new StackOverflowQuestion(
                99L, "Test title", "https://stackoverflow.com/questions/99",
                null, List.of("java"), "body", 1700000000L, 5, 100, false
        );
        StackOverflowApiResponse apiResponse = new StackOverflowApiResponse(
                List.of(question), false, 9990
        );

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class))
                .willReturn(Mono.just(apiResponse));

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).author()).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("fetchQuestions — body 300자 초과면 preview 잘림 처리")
    void fetchQuestions_longBody_previewTruncated() {
        String longBody = "x".repeat(400);
        StackOverflowQuestion.Owner owner = new StackOverflowQuestion.Owner("Jane", 1L);
        StackOverflowQuestion question = new StackOverflowQuestion(
                1L, "Long title", "https://stackoverflow.com/questions/1",
                owner, List.of(), longBody, 1700000000L, 1, 10, false
        );
        StackOverflowApiResponse apiResponse = new StackOverflowApiResponse(List.of(question), false, 9990);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class))
                .willReturn(Mono.just(apiResponse));

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result.get(0).preview()).endsWith("...");
        assertThat(result.get(0).preview()).hasSize(303); // 300 + "..."
    }

    @Test
    @DisplayName("collect — save 시 Content의 source, title, canonicalUrl 올바르게 매핑")
    void collect_contentMapping_correct() {
        given(contentSourceRepository.findByNameAndIsActiveTrue("Stack Overflow"))
                .willReturn(Optional.of(stackOverflowSource));

        StackOverflowCollector spyCollector = spy(collector);
        CollectedContent item = buildCollectedContent("https://stackoverflow.com/q/999");
        doReturn(List.of(item)).when(spyCollector).fetchItems(anyString());

        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        given(contentRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

        spyCollector.collect("java");

        Content saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("Test Question Title");
        assertThat(saved.getCanonicalUrl()).isEqualTo("https://stackoverflow.com/q/999");
        assertThat(saved.getIsOriginalVisible()).isTrue();
        assertThat(saved.getLicenseType()).isEqualTo("CC BY-SA 4.0");
        assertThat(saved.getSource()).isEqualTo(stackOverflowSource);
    }

    @Test
    @DisplayName("fetchQuestions — response.items()가 null이면 빈 리스트 반환")
    void fetchQuestions_nullItems_returnsEmpty() {
        StackOverflowApiResponse apiResponse = new StackOverflowApiResponse(null, false, 9990);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class))
                .willReturn(Mono.just(apiResponse));

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchQuestions — WebClientResponseException 발생 시 빈 리스트 반환")
    void fetchQuestions_webClientResponseException_returnsEmpty() {
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class))
                .willReturn(Mono.error(
                        WebClientResponseException.create(429, "Too Many Requests", null, null, null)
                ));

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchQuestions — tags null이면 tagged 파라미터 없이 URI 생성")
    void fetchQuestions_nullTags_buildUriWithoutTagged() {
        StackOverflowApiResponse apiResponse = new StackOverflowApiResponse(List.of(), false, 9990);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class))
                .willReturn(Mono.just(apiResponse));

        List<CollectedContent> result = collector.fetchQuestions(null);

        assertThat(result).isEmpty();
        verify(requestHeadersUriSpec).uri(argThat((String uri) -> !uri.contains("tagged=")));
    }

    @Test
    @DisplayName("fetchQuestions — owner는 있지만 displayName null이면 author Unknown 처리")
    void fetchQuestions_ownerWithNullDisplayName_usesUnknownAuthor() {
        StackOverflowQuestion.Owner ownerNoName = new StackOverflowQuestion.Owner(null, 42L);
        StackOverflowQuestion question = new StackOverflowQuestion(
                10L, "No-name question", "https://stackoverflow.com/questions/10",
                ownerNoName, List.of("java"), "body text", 1700000000L, 1, 10, false
        );
        StackOverflowApiResponse apiResponse = new StackOverflowApiResponse(List.of(question), false, 9990);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class))
                .willReturn(Mono.just(apiResponse));

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).author()).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("fetchQuestions — bodyMarkdown null이면 preview도 null로 처리")
    void fetchQuestions_nullBodyMarkdown_previewNull() {
        StackOverflowQuestion.Owner owner = new StackOverflowQuestion.Owner("Dev", 1L);
        StackOverflowQuestion question = new StackOverflowQuestion(
                20L, "Null body question", "https://stackoverflow.com/questions/20",
                owner, List.of("spring"), null, 1700000000L, 0, 5, false
        );
        StackOverflowApiResponse apiResponse = new StackOverflowApiResponse(List.of(question), false, 9990);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class))
                .willReturn(Mono.just(apiResponse));

        List<CollectedContent> result = collector.fetchQuestions("spring");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).preview()).isNull();
        assertThat(result.get(0).originalContent()).isNull();
    }

    @Test
    @DisplayName("fetchQuestions — tags null이면 빈 리스트로 처리")
    void fetchQuestions_nullTags_usesEmptyList() {
        StackOverflowQuestion.Owner owner = new StackOverflowQuestion.Owner("Dev", 1L);
        StackOverflowQuestion question = new StackOverflowQuestion(
                30L, "No tags question", "https://stackoverflow.com/questions/30",
                owner, null, "body", 1700000000L, 0, 5, false
        );
        StackOverflowApiResponse apiResponse = new StackOverflowApiResponse(List.of(question), false, 9990);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class))
                .willReturn(Mono.just(apiResponse));

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tags()).isEmpty();
    }

    @Test
    @DisplayName("fetchQuestions — apiKey 설정 시 key 파라미터 URI에 포함")
    void fetchQuestions_withApiKey_includesKeyInUri() throws Exception {
        // apiKey 필드에 값 주입
        java.lang.reflect.Field field = StackOverflowCollector.class.getDeclaredField("apiKey");
        field.setAccessible(true);
        field.set(collector, "test-api-key-1234");

        StackOverflowApiResponse apiResponse = new StackOverflowApiResponse(List.of(), false, 9990);
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class))
                .willReturn(Mono.just(apiResponse));

        collector.fetchQuestions("java");

        verify(requestHeadersUriSpec).uri(argThat((String uri) -> uri.contains("key=test-api-key-1234")));

        // 원래대로 복원
        field.set(collector, "");
    }

    @Test
    @DisplayName("fetchQuestions — tags 빈 문자열이면 tagged 파라미터 없이 URI 생성")
    void fetchQuestions_blankTags_buildUriWithoutTagged() {
        StackOverflowApiResponse apiResponse = new StackOverflowApiResponse(List.of(), false, 9990);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class))
                .willReturn(Mono.just(apiResponse));

        List<CollectedContent> result = collector.fetchQuestions("   ");

        assertThat(result).isEmpty();
        verify(requestHeadersUriSpec).uri(argThat((String uri) -> !uri.contains("tagged=")));
    }

    // ── Commit 4: 새 기능 테스트 ──────────────────────────────────────────────────

    @Test
    @DisplayName("fetchQuestions — URI에 sort=votes 포함")
    void fetchQuestions_uri_contains_sort_votes() {
        StackOverflowApiResponse apiResponse = new StackOverflowApiResponse(List.of(), false, 9990);
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class)).willReturn(Mono.just(apiResponse));

        collector.fetchQuestions("java");

        verify(requestHeadersUriSpec).uri(argThat((String uri) -> uri.contains("sort=votes")));
    }

    @Test
    @DisplayName("fetchQuestions — URI에 fromdate 포함")
    void fetchQuestions_uri_contains_fromdate() {
        StackOverflowApiResponse apiResponse = new StackOverflowApiResponse(List.of(), false, 9990);
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class)).willReturn(Mono.just(apiResponse));

        collector.fetchQuestions("java");

        verify(requestHeadersUriSpec).uri(argThat((String uri) -> uri.contains("fromdate=")));
    }

    @Test
    @DisplayName("fetchQuestions — isAnswered=true 질문의 채택/상위 답변 정상 매핑")
    void fetchQuestions_answeredQuestion_acceptedAndTopAnswersPopulated() {
        StackOverflowQuestion.Owner owner = new StackOverflowQuestion.Owner("Jane", 1L);
        StackOverflowQuestion question = new StackOverflowQuestion(
                100L, "Spring Boot 질문", "https://stackoverflow.com/questions/100",
                owner, List.of("java"), "question body", 1700000000L, 5, 100, true);
        StackOverflowApiResponse questionsResponse = new StackOverflowApiResponse(List.of(question), false, 9990);

        StackOverflowAnswer acceptedAns = new StackOverflowAnswer(200L, 100L, "accepted body", 50, true);
        StackOverflowAnswer topAns = new StackOverflowAnswer(201L, 100L, "top body", 30, false);
        StackOverflowAnswerApiResponse answersResponse = new StackOverflowAnswerApiResponse(
                List.of(acceptedAns, topAns), false, 9989);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class)).willReturn(Mono.just(questionsResponse));
        given(responseSpec.bodyToMono(StackOverflowAnswerApiResponse.class)).willReturn(Mono.just(answersResponse));

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result).hasSize(1);
        CollectedContent content = result.get(0);
        assertThat(content.acceptedAnswer()).isNotNull();
        assertThat(content.acceptedAnswer().body()).isEqualTo("accepted body");
        assertThat(content.acceptedAnswer().score()).isEqualTo(50);
        assertThat(content.topAnswers()).hasSize(1);
        assertThat(content.topAnswers().get(0).body()).isEqualTo("top body");
    }

    @Test
    @DisplayName("fetchQuestions — isAnswered=false면 답변 API 호출 안 함")
    void fetchQuestions_unansweredQuestion_noAnswerApiFetch() {
        StackOverflowQuestion.Owner owner = new StackOverflowQuestion.Owner("Jane", 1L);
        StackOverflowQuestion question = new StackOverflowQuestion(
                100L, "미답변 질문", "https://stackoverflow.com/questions/100",
                owner, List.of("java"), "question body", 1700000000L, 0, 50, false);
        StackOverflowApiResponse questionsResponse = new StackOverflowApiResponse(List.of(question), false, 9990);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class)).willReturn(Mono.just(questionsResponse));

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).acceptedAnswer()).isNull();
        assertThat(result.get(0).topAnswers()).isNull();
        verify(responseSpec, never()).bodyToMono(StackOverflowAnswerApiResponse.class);
    }

    @Test
    @DisplayName("fetchQuestions — 답변 수집 실패해도 질문 콘텐츠 반환 (graceful degradation)")
    void fetchQuestions_answerFetchFails_returnsContentWithNullAnswers() {
        StackOverflowQuestion.Owner owner = new StackOverflowQuestion.Owner("Dev", 1L);
        StackOverflowQuestion question = new StackOverflowQuestion(
                101L, "질문", "https://stackoverflow.com/questions/101",
                owner, List.of("java"), "body", 1700000000L, 3, 20, true);
        StackOverflowApiResponse questionsResponse = new StackOverflowApiResponse(List.of(question), false, 9990);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class)).willReturn(Mono.just(questionsResponse));
        given(responseSpec.bodyToMono(StackOverflowAnswerApiResponse.class))
                .willReturn(Mono.error(new RuntimeException("answer API down")));

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("질문");
        assertThat(result.get(0).acceptedAnswer()).isNull();
        assertThat(result.get(0).topAnswers()).isNull();
    }

    @Test
    @DisplayName("fetchQuestions — 답변 bodyMarkdown null이면 필터링")
    void fetchQuestions_answerNullBody_filtered() {
        StackOverflowQuestion.Owner owner = new StackOverflowQuestion.Owner("Dev", 1L);
        StackOverflowQuestion question = new StackOverflowQuestion(
                102L, "질문", "https://stackoverflow.com/questions/102",
                owner, List.of(), "body", 1700000000L, 1, 10, true);
        StackOverflowApiResponse questionsResponse = new StackOverflowApiResponse(List.of(question), false, 9990);

        StackOverflowAnswer nullBodyAnswer = new StackOverflowAnswer(300L, 102L, null, 10, true);
        StackOverflowAnswerApiResponse answersResponse = new StackOverflowAnswerApiResponse(
                List.of(nullBodyAnswer), false, 9989);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class)).willReturn(Mono.just(questionsResponse));
        given(responseSpec.bodyToMono(StackOverflowAnswerApiResponse.class)).willReturn(Mono.just(answersResponse));

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).acceptedAnswer()).isNull();
        assertThat(result.get(0).topAnswers()).isNull();
    }

    @Test
    @DisplayName("fetchQuestions — topAnswers 점수 내림차순, 최대 2개")
    void fetchQuestions_topAnswers_sortedByScore_maxTwo() {
        StackOverflowQuestion.Owner owner = new StackOverflowQuestion.Owner("Dev", 1L);
        StackOverflowQuestion question = new StackOverflowQuestion(
                103L, "질문", "https://stackoverflow.com/questions/103",
                owner, List.of(), "body", 1700000000L, 5, 30, true);
        StackOverflowApiResponse questionsResponse = new StackOverflowApiResponse(List.of(question), false, 9990);

        StackOverflowAnswer ans1 = new StackOverflowAnswer(400L, 103L, "score 10", 10, false);
        StackOverflowAnswer ans2 = new StackOverflowAnswer(401L, 103L, "score 50", 50, false);
        StackOverflowAnswer ans3 = new StackOverflowAnswer(402L, 103L, "score 30", 30, false);
        StackOverflowAnswerApiResponse answersResponse = new StackOverflowAnswerApiResponse(
                List.of(ans1, ans2, ans3), false, 9989);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class)).willReturn(Mono.just(questionsResponse));
        given(responseSpec.bodyToMono(StackOverflowAnswerApiResponse.class)).willReturn(Mono.just(answersResponse));

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result.get(0).topAnswers()).hasSize(2);
        assertThat(result.get(0).topAnswers().get(0).score()).isEqualTo(50);
        assertThat(result.get(0).topAnswers().get(1).score()).isEqualTo(30);
    }

    @Test
    @DisplayName("fetchQuestions — originalContent에 질문과 답변 병합")
    void fetchQuestions_originalContent_mergesQuestionAndAnswers() {
        StackOverflowQuestion.Owner owner = new StackOverflowQuestion.Owner("Dev", 1L);
        StackOverflowQuestion question = new StackOverflowQuestion(
                104L, "질문", "https://stackoverflow.com/questions/104",
                owner, List.of(), "질문 본문", 1700000000L, 5, 20, true);
        StackOverflowApiResponse questionsResponse = new StackOverflowApiResponse(List.of(question), false, 9990);

        StackOverflowAnswer accepted = new StackOverflowAnswer(500L, 104L, "채택 답변 본문", 20, true);
        StackOverflowAnswer top = new StackOverflowAnswer(501L, 104L, "상위 답변 본문", 10, false);
        StackOverflowAnswerApiResponse answersResponse = new StackOverflowAnswerApiResponse(
                List.of(accepted, top), false, 9989);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class)).willReturn(Mono.just(questionsResponse));
        given(responseSpec.bodyToMono(StackOverflowAnswerApiResponse.class)).willReturn(Mono.just(answersResponse));

        List<CollectedContent> result = collector.fetchQuestions("java");

        String originalContent = result.get(0).originalContent();
        assertThat(originalContent).contains("## Question").contains("질문 본문");
        assertThat(originalContent).contains("## Accepted Answer").contains("채택 답변 본문");
        assertThat(originalContent).contains("## Top Answers").contains("상위 답변 본문");
    }

    @Test
    @DisplayName("fetchQuestions — questionContent는 질문 bodyMarkdown")
    void fetchQuestions_questionContent_equalsBodyMarkdown() {
        StackOverflowQuestion.Owner owner = new StackOverflowQuestion.Owner("Dev", 1L);
        StackOverflowQuestion question = new StackOverflowQuestion(
                105L, "질문", "https://stackoverflow.com/questions/105",
                owner, List.of(), "질문 본문 텍스트", 1700000000L, 2, 10, false);
        StackOverflowApiResponse questionsResponse = new StackOverflowApiResponse(List.of(question), false, 9990);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class)).willReturn(Mono.just(questionsResponse));

        List<CollectedContent> result = collector.fetchQuestions("java");

        assertThat(result.get(0).questionContent()).isEqualTo("질문 본문 텍스트");
    }

    @Test
    @DisplayName("fetchQuestions — SO 필드(score, viewCount, isAnswered) 올바르게 매핑")
    void fetchQuestions_soFields_mappedCorrectly() {
        StackOverflowQuestion.Owner owner = new StackOverflowQuestion.Owner("Dev", 1L);
        StackOverflowQuestion question = new StackOverflowQuestion(
                106L, "질문", "https://stackoverflow.com/questions/106",
                owner, List.of(), "body", 1700000000L, 42, 999, false);
        StackOverflowApiResponse response = new StackOverflowApiResponse(List.of(question), false, 9990);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowApiResponse.class)).willReturn(Mono.just(response));

        List<CollectedContent> result = collector.fetchQuestions("java");
        CollectedContent content = result.get(0);

        assertThat(content.score()).isEqualTo(42);
        assertThat(content.viewCount()).isEqualTo(999);
        assertThat(content.isAnswered()).isFalse();
    }

    @Test
    @DisplayName("fetchAnswersBatch — 정상 응답 시 questionId 기준 그룹핑")
    void fetchAnswersBatch_success_groupedByQuestionId() {
        StackOverflowAnswer ans1 = new StackOverflowAnswer(600L, 10L, "body1", 5, false);
        StackOverflowAnswer ans2 = new StackOverflowAnswer(601L, 10L, "body2", 3, false);
        StackOverflowAnswer ans3 = new StackOverflowAnswer(602L, 20L, "body3", 10, true);
        StackOverflowAnswerApiResponse response = new StackOverflowAnswerApiResponse(
                List.of(ans1, ans2, ans3), false, 9990);

        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowAnswerApiResponse.class)).willReturn(Mono.just(response));

        Map<Long, List<StackOverflowAnswer>> result = collector.fetchAnswersBatch(List.of(10L, 20L));

        assertThat(result).containsKey(10L);
        assertThat(result).containsKey(20L);
        assertThat(result.get(10L)).hasSize(2);
        assertThat(result.get(20L)).hasSize(1);
    }

    @Test
    @DisplayName("fetchAnswersBatch — null 응답 시 빈 맵 반환")
    void fetchAnswersBatch_nullResponse_returnsEmptyMap() {
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowAnswerApiResponse.class)).willReturn(Mono.empty());

        Map<Long, List<StackOverflowAnswer>> result = collector.fetchAnswersBatch(List.of(10L));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetchAnswersBatch — API 오류 시 빈 맵 반환 (graceful degradation)")
    void fetchAnswersBatch_exception_returnsEmptyMap() {
        given(webClient.get()).willReturn(requestHeadersUriSpec);
        given(requestHeadersUriSpec.uri(anyString())).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(StackOverflowAnswerApiResponse.class))
                .willReturn(Mono.error(new RuntimeException("API error")));

        Map<Long, List<StackOverflowAnswer>> result = collector.fetchAnswersBatch(List.of(10L, 20L));

        assertThat(result).isEmpty();
    }

    private CollectedContent buildCollectedContent(String url) {
        return CollectedContent.of(
                "Test Question Title",
                "TestAuthor",
                url,
                "preview text",
                "full body",
                true,
                "CC BY-SA 4.0",
                java.time.LocalDateTime.now(),
                List.of("java", "spring-boot")
        );
    }
}
