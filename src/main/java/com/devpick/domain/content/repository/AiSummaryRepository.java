package com.devpick.domain.content.repository;

import com.devpick.domain.content.document.AiSummaryDocument;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AiSummaryRepository {

    private static final String TABLE_NAME = "ai_summaries";

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<AiSummaryDocument> table;

    public AiSummaryRepository(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(AiSummaryDocument.class));
    }

    public Optional<AiSummaryDocument> findByContentIdAndLevel(String contentId, String level) {
        Key key = Key.builder()
                .partitionValue(contentId)
                .sortValue(level)
                .build();
        return Optional.ofNullable(table.getItem(key));
    }

    /** contentId 목록에 대해 지정 level의 coreSummary를 일괄 조회한다. */
    public Map<UUID, String> batchFindCoreSummaries(List<String> contentIds, String level) {
        if (contentIds.isEmpty()) return Map.of();

        ReadBatch.Builder<AiSummaryDocument> batchBuilder = ReadBatch.builder(AiSummaryDocument.class)
                .mappedTableResource(table);
        contentIds.forEach(id -> batchBuilder.addGetItem(
                Key.builder().partitionValue(id).sortValue(level).build()));

        Map<UUID, String> result = new HashMap<>();
        enhancedClient.batchGetItem(
                BatchGetItemEnhancedRequest.builder().readBatches(batchBuilder.build()).build()
        ).resultsForTable(table).forEach(doc -> {
            if (doc.getCoreSummary() != null) {
                result.put(UUID.fromString(doc.getContentId()), doc.getCoreSummary());
            }
        });
        return result;
    }
}
