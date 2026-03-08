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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        doReturn(List.of(item1, item2)).when(spyCollector).fetchQuestions(anyString());
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
        doReturn(List.of(item)).when(spyCollector).fetchQuestions(anyString());
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
        doReturn(List.of(item1, item2)).when(spyCollector).fetchQuestions(anyString());

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
        doReturn(List.of(item)).when(spyCollector).fetchQuestions(anyString());

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

    private CollectedContent buildCollectedContent(String url) {
        return new CollectedContent(
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
