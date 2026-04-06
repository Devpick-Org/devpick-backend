package com.devpick.domain.content.document;

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
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.LocalDateTime;
import java.util.List;

@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class AiSummaryDocument {

    private String contentId;
    private String level;
    private String coreSummary;
    private List<String> keyPoints;
    private List<String> keywords;
    private String difficulty;
    private String nextRecommendation;
    private Double confidence;
    private List<String> additionalQuestions;
    private LocalDateTime cachedAt;
    private LocalDateTime expiresAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("content_id")
    public String getContentId() {
        return contentId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("level")
    public String getLevel() {
        return level;
    }

    @DynamoDbAttribute("core_summary")
    public String getCoreSummary() {
        return coreSummary;
    }

    @DynamoDbAttribute("key_points")
    public List<String> getKeyPoints() {
        return keyPoints;
    }

    @DynamoDbAttribute("keywords")
    public List<String> getKeywords() {
        return keywords;
    }

    @DynamoDbAttribute("difficulty")
    public String getDifficulty() {
        return difficulty;
    }

    @DynamoDbAttribute("next_recommendation")
    public String getNextRecommendation() {
        return nextRecommendation;
    }

    @DynamoDbAttribute("confidence")
    public Double getConfidence() {
        return confidence;
    }

    @DynamoDbAttribute("additional_questions")
    public List<String> getAdditionalQuestions() {
        return additionalQuestions;
    }

    @DynamoDbConvertedBy(LocalDateTimeConverter.class)
    @DynamoDbAttribute("cached_at")
    public LocalDateTime getCachedAt() {
        return cachedAt;
    }

    @DynamoDbConvertedBy(LocalDateTimeConverter.class)
    @DynamoDbAttribute("expires_at")
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
