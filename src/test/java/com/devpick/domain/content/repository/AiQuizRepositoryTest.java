package com.devpick.domain.content.repository;

import com.devpick.domain.content.document.AiQuizDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPageIterable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiQuizRepositoryTest {

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @SuppressWarnings("unchecked")
    @Mock
    private DynamoDbTable<AiQuizDocument> table;

    private AiQuizRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        given(enhancedClient.table(any(), any())).willReturn((DynamoDbTable) table);
        lenient().when(table.tableSchema()).thenReturn(TableSchema.fromBean(AiQuizDocument.class));
        repository = new AiQuizRepository(enhancedClient);
    }

    @Test
    @DisplayName("findByContentIdAndLevel — 항목 있으면 Optional.of 반환")
    void findByContentIdAndLevel_found_returnsOptionalOf() {
        AiQuizDocument doc = AiQuizDocument.builder()
                .contentId("c-1").level("junior").title("Spring 퀴즈").build();
        given(table.getItem(any(Key.class))).willReturn(doc);

        Optional<AiQuizDocument> result = repository.findByContentIdAndLevel("c-1", "junior");

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Spring 퀴즈");
    }

    @Test
    @DisplayName("findByContentIdAndLevel — 항목 없으면 Optional.empty 반환")
    void findByContentIdAndLevel_notFound_returnsEmpty() {
        given(table.getItem(any(Key.class))).willReturn(null);

        Optional<AiQuizDocument> result = repository.findByContentIdAndLevel("no-id", "junior");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("batchFindFirstQuestions — 빈 목록은 DynamoDB 호출 없이 빈 맵 반환")
    void batchFindFirstQuestions_emptyList_returnsEmptyMapWithoutDbCall() {
        Map<String, String> result = repository.batchFindFirstQuestions(List.of());

        assertThat(result).isEmpty();
        verify(enhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("batchFindFirstQuestions — questions 있는 문서 → 첫 번째 문제 텍스트 반환")
    @SuppressWarnings("unchecked")
    void batchFindFirstQuestions_withQuestions_returnsFirstQuestionText() {
        AiQuizDocument.Question q = AiQuizDocument.Question.builder()
                .id("q-1").type("multiple_choice").question("첫 번째 문제").build();
        AiQuizDocument doc = AiQuizDocument.builder()
                .contentId("c-1").level("junior").questions(List.of(q)).build();

        BatchGetResultPageIterable resultPages = mock(BatchGetResultPageIterable.class);
        given(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class))).willReturn(resultPages);
        SdkIterable<AiQuizDocument> iterable = () -> List.of(doc).iterator();
        doReturn(iterable).when(resultPages).resultsForTable(any());

        Map<String, String> result = repository.batchFindFirstQuestions(
                List.<String[]>of(new String[]{"c-1", "junior"}));

        assertThat(result).containsEntry("c-1|junior", "첫 번째 문제");
    }

    @Test
    @DisplayName("batchFindFirstQuestions — questions null인 문서 → 맵에서 제외")
    @SuppressWarnings("unchecked")
    void batchFindFirstQuestions_withNullQuestions_excludesFromMap() {
        AiQuizDocument doc = AiQuizDocument.builder()
                .contentId("c-1").level("junior").questions(null).build();

        BatchGetResultPageIterable resultPages = mock(BatchGetResultPageIterable.class);
        given(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class))).willReturn(resultPages);
        SdkIterable<AiQuizDocument> iterable = () -> List.of(doc).iterator();
        doReturn(iterable).when(resultPages).resultsForTable(any());

        Map<String, String> result = repository.batchFindFirstQuestions(
                List.<String[]>of(new String[]{"c-1", "junior"}));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("batchFindFirstQuestions — questions 빈 리스트인 문서 → 맵에서 제외")
    @SuppressWarnings("unchecked")
    void batchFindFirstQuestions_withEmptyQuestions_excludesFromMap() {
        AiQuizDocument doc = AiQuizDocument.builder()
                .contentId("c-1").level("junior").questions(List.of()).build();

        BatchGetResultPageIterable resultPages = mock(BatchGetResultPageIterable.class);
        given(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class))).willReturn(resultPages);
        SdkIterable<AiQuizDocument> iterable = () -> List.of(doc).iterator();
        doReturn(iterable).when(resultPages).resultsForTable(any());

        Map<String, String> result = repository.batchFindFirstQuestions(
                List.<String[]>of(new String[]{"c-1", "junior"}));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("batchFindFirstQuestions — 중복 키는 한 번만 조회되어 단일 결과 반환")
    @SuppressWarnings("unchecked")
    void batchFindFirstQuestions_duplicateKeys_deduplicatesAndReturnsOneEntry() {
        AiQuizDocument.Question q = AiQuizDocument.Question.builder()
                .id("q-1").question("중복 문제").build();
        AiQuizDocument doc = AiQuizDocument.builder()
                .contentId("c-1").level("junior").questions(List.of(q)).build();

        BatchGetResultPageIterable resultPages = mock(BatchGetResultPageIterable.class);
        given(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class))).willReturn(resultPages);
        SdkIterable<AiQuizDocument> iterable = () -> List.of(doc).iterator();
        doReturn(iterable).when(resultPages).resultsForTable(any());

        Map<String, String> result = repository.batchFindFirstQuestions(
                List.<String[]>of(new String[]{"c-1", "junior"}, new String[]{"c-1", "junior"}));

        assertThat(result).hasSize(1).containsEntry("c-1|junior", "중복 문제");
    }

    @Test
    @DisplayName("batchFindFirstQuestions — 여러 문서에 대해 각각 첫 번째 문제 텍스트 반환")
    @SuppressWarnings("unchecked")
    void batchFindFirstQuestions_multipleDocs_returnsAllFirstQuestions() {
        AiQuizDocument.Question q1 = AiQuizDocument.Question.builder().id("q-1").question("문제1").build();
        AiQuizDocument.Question q2 = AiQuizDocument.Question.builder().id("q-2").question("문제2").build();
        AiQuizDocument doc1 = AiQuizDocument.builder().contentId("c-1").level("junior").questions(List.of(q1)).build();
        AiQuizDocument doc2 = AiQuizDocument.builder().contentId("c-2").level("mid").questions(List.of(q2)).build();

        BatchGetResultPageIterable resultPages = mock(BatchGetResultPageIterable.class);
        given(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class))).willReturn(resultPages);
        SdkIterable<AiQuizDocument> iterable = () -> List.of(doc1, doc2).iterator();
        doReturn(iterable).when(resultPages).resultsForTable(any());

        Map<String, String> result = repository.batchFindFirstQuestions(
                List.<String[]>of(new String[]{"c-1", "junior"}, new String[]{"c-2", "mid"}));

        assertThat(result).hasSize(2)
                .containsEntry("c-1|junior", "문제1")
                .containsEntry("c-2|mid", "문제2");
    }
}
