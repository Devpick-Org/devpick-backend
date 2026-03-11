package com.devpick.domain.content.document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "ai_summaries")
@CompoundIndex(name = "idx_content_level", def = "{'content_id': 1, 'level': 1}", unique = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AiSummaryDocument {

    @Id
    private String id;

    @Field("content_id")
    private String contentId;

    @Field("level")
    private String level;

    @Field("core_summary")
    private String coreSummary;

    @Field("key_points")
    private List<String> keyPoints;

    @Field("keywords")
    private List<String> keywords;

    @Field("difficulty")
    private String difficulty;

    @Field("next_recommendation")
    private String nextRecommendation;

    @Field("confidence")
    private Double confidence;

    @Field("additional_questions")
    private List<String> additionalQuestions;

    @Field("cached_at")
    private LocalDateTime cachedAt;

    @Field("expires_at")
    private LocalDateTime expiresAt;
}
