package com.devpick.domain.content.repository;

import com.devpick.domain.content.document.AiSummaryDocument;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

@Repository
public class AiSummaryRepository {

    private static final String TABLE_NAME = "ai_summaries";

    private final DynamoDbTable<AiSummaryDocument> table;

    public AiSummaryRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(AiSummaryDocument.class));
    }

    public Optional<AiSummaryDocument> findByContentIdAndLevel(String contentId, String level) {
        Key key = Key.builder()
                .partitionValue(contentId)
                .sortValue(level)
                .build();
        return Optional.ofNullable(table.getItem(key));
    }

    public AiSummaryDocument save(AiSummaryDocument document) {
        table.putItem(document);
        return document;
    }

    public void deleteByContentIdAndLevel(String contentId, String level) {
        Key key = Key.builder()
                .partitionValue(contentId)
                .sortValue(level)
                .build();
        table.deleteItem(key);
    }
}
