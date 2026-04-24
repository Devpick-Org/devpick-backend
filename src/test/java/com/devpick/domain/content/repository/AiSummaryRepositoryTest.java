package com.devpick.domain.content.repository;

import com.devpick.domain.content.document.AiSummaryDocument;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class
AiSummaryRepositoryTest {

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @SuppressWarnings("unchecked")
    @Mock
    private DynamoDbTable<AiSummaryDocument> table;

    private AiSummaryRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        given(enhancedClient.table(any(), any())).willReturn((DynamoDbTable) table);
        lenient().when(table.tableSchema()).thenReturn(TableSchema.fromBean(AiSummaryDocument.class));
        repository = new AiSummaryRepository(enhancedClient);
    }

    @Test
    @DisplayName("findByContentIdAndLevel — 항목 있으면 Optional.of 반환")
    void findByContentIdAndLevel_found_returnsOptionalOf() {
        AiSummaryDocument doc = AiSummaryDocument.builder()
                .contentId("content-1").level("junior").coreSummary("요약").build();
        given(table.getItem(any(Key.class))).willReturn(doc);

        Optional<AiSummaryDocument> result = repository.findByContentIdAndLevel("content-1", "junior");

        assertThat(result).isPresent();
        assertThat(result.get().getCoreSummary()).isEqualTo("요약");
    }

    @Test
    @DisplayName("findByContentIdAndLevel — 항목 없으면 Optional.empty 반환")
    void findByContentIdAndLevel_notFound_returnsEmpty() {
        given(table.getItem(any(Key.class))).willReturn(null);

        Optional<AiSummaryDocument> result = repository.findByContentIdAndLevel("no-id", "junior");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("batchFindCoreSummaries — 빈 목록은 DynamoDB 호출 없이 빈 맵 반환")
    void batchFindCoreSummaries_emptyList_returnsEmptyMapWithoutDbCall() {
        Map<UUID, String> result = repository.batchFindCoreSummaries(List.of(), "junior");

        assertThat(result).isEmpty();
        verify(enhancedClient, never()).batchGetItem(any(BatchGetItemEnhancedRequest.class));
    }

    @Test
    @DisplayName("batchFindCoreSummaries — coreSummary 있는 항목은 UUID 키로 맵에 포함")
    @SuppressWarnings("unchecked")
    void batchFindCoreSummaries_withSummaries_returnsCoreSummaryMap() {
        UUID contentId = UUID.randomUUID();
        AiSummaryDocument doc = AiSummaryDocument.builder()
                .contentId(contentId.toString()).level("junior").coreSummary("핵심 요약 내용").build();

        BatchGetResultPageIterable resultPages = mock(BatchGetResultPageIterable.class);
        given(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class))).willReturn(resultPages);
        SdkIterable<AiSummaryDocument> iterable = () -> List.of(doc).iterator();
        doReturn(iterable).when(resultPages).resultsForTable(any());

        Map<UUID, String> result = repository.batchFindCoreSummaries(
                List.of(contentId.toString()), "junior");

        assertThat(result).containsEntry(contentId, "핵심 요약 내용");
    }

    @Test
    @DisplayName("batchFindCoreSummaries — coreSummary null인 항목은 맵에서 제외")
    @SuppressWarnings("unchecked")
    void batchFindCoreSummaries_withNullSummary_excludesFromMap() {
        UUID contentId = UUID.randomUUID();
        AiSummaryDocument doc = AiSummaryDocument.builder()
                .contentId(contentId.toString()).level("junior").coreSummary(null).build();

        BatchGetResultPageIterable resultPages = mock(BatchGetResultPageIterable.class);
        given(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class))).willReturn(resultPages);
        SdkIterable<AiSummaryDocument> iterable = () -> List.of(doc).iterator();
        doReturn(iterable).when(resultPages).resultsForTable(any());

        Map<UUID, String> result = repository.batchFindCoreSummaries(
                List.of(contentId.toString()), "junior");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("batchFindCoreSummaries — 여러 콘텐츠 ID에 대해 모두 맵에 포함")
    @SuppressWarnings("unchecked")
    void batchFindCoreSummaries_multipleIds_returnsAllSummaries() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        AiSummaryDocument doc1 = AiSummaryDocument.builder()
                .contentId(id1.toString()).level("junior").coreSummary("요약1").build();
        AiSummaryDocument doc2 = AiSummaryDocument.builder()
                .contentId(id2.toString()).level("junior").coreSummary("요약2").build();

        BatchGetResultPageIterable resultPages = mock(BatchGetResultPageIterable.class);
        given(enhancedClient.batchGetItem(any(BatchGetItemEnhancedRequest.class))).willReturn(resultPages);
        SdkIterable<AiSummaryDocument> iterable = () -> List.of(doc1, doc2).iterator();
        doReturn(iterable).when(resultPages).resultsForTable(any());

        Map<UUID, String> result = repository.batchFindCoreSummaries(
                List.of(id1.toString(), id2.toString()), "junior");

        assertThat(result).hasSize(2)
                .containsEntry(id1, "요약1")
                .containsEntry(id2, "요약2");
    }
}
