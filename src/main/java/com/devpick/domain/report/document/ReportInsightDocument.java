package com.devpick.domain.report.document;

import com.devpick.global.config.LocalDateTimeConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.LocalDateTime;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class ReportInsightDocument {

    private String reportId;
    private String userId;
    private String wellDone;
    private String lacking;
    private String nextWeek;
    private LocalDateTime generatedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("report_id")
    public String getReportId() {
        return reportId;
    }

    @DynamoDbAttribute("user_id")
    public String getUserId() {
        return userId;
    }

    @DynamoDbAttribute("well_done")
    public String getWellDone() {
        return wellDone;
    }

    @DynamoDbAttribute("lacking")
    public String getLacking() {
        return lacking;
    }

    @DynamoDbAttribute("next_week")
    public String getNextWeek() {
        return nextWeek;
    }

    @DynamoDbConvertedBy(LocalDateTimeConverter.class)
    @DynamoDbAttribute("generated_at")
    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }
}
