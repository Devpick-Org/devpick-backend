package com.devpick.domain.content.repository;

import com.devpick.domain.content.document.AiQuizDocument;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class AiQuizRepository {

    private static final String TABLE_NAME = "ai_quizzes";

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<AiQuizDocument> table;

    public AiQuizRepository(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(AiQuizDocument.class));
    }

    public Optional<AiQuizDocument> findByContentIdAndLevel(String contentId, String level) {
        Key key = Key.builder()
                .partitionValue(contentId)
                .sortValue(level)
                .build();
        return Optional.ofNullable(table.getItem(key));
    }

    public AiQuizDocument save(AiQuizDocument document) {
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

    /**
     * (contentId, level) 쌍 목록에 대해 첫 번째 문제 텍스트를 배치 조회한다.
     * @param keys [contentId, level] 배열의 리스트
     * @return "contentId|level" → 첫 번째 문제 텍스트
     */
    public Map<String, String> batchFindFirstQuestions(List<String[]> keys) {
        if (keys.isEmpty()) return Map.of();

        ReadBatch.Builder<AiQuizDocument> batchBuilder = ReadBatch.builder(AiQuizDocument.class)
                .mappedTableResource(table);

        Set<String> seen = new HashSet<>();
        for (String[] key : keys) {
            if (seen.add(key[0] + "|" + key[1])) {
                batchBuilder.addGetItem(Key.builder()
                        .partitionValue(key[0]).sortValue(key[1]).build());
            }
        }

        Map<String, String> result = new HashMap<>();
        enhancedClient.batchGetItem(
                BatchGetItemEnhancedRequest.builder().readBatches(batchBuilder.build()).build()
        ).resultsForTable(table).forEach(doc -> {
            if (doc.getQuestions() != null && !doc.getQuestions().isEmpty()) {
                result.put(doc.getContentId() + "|" + doc.getLevel(),
                        doc.getQuestions().getFirst().getQuestion());
            }
        });
        return result;
    }
}
