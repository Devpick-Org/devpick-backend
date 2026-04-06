package com.devpick.domain.report.repository;

import com.devpick.domain.report.document.ReportInsightDocument;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

@Repository
public class ReportInsightRepository {

    private static final String TABLE_NAME = "weekly_report_insights";

    private final DynamoDbTable<ReportInsightDocument> table;

    public ReportInsightRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(ReportInsightDocument.class));
    }

    public Optional<ReportInsightDocument> findByReportId(String reportId) {
        Key key = Key.builder()
                .partitionValue(reportId)
                .build();
        return Optional.ofNullable(table.getItem(key));
    }

    public ReportInsightDocument save(ReportInsightDocument document) {
        table.putItem(document);
        return document;
    }
}
