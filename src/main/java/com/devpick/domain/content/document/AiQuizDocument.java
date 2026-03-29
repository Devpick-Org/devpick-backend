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

@Document(collection = "ai_quizzes")
@CompoundIndex(name = "idx_quiz_content_level", def = "{'content_id': 1, 'level': 1}", unique = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AiQuizDocument {

    @Id
    private String id;

    @Field("content_id")
    private String contentId;

    @Field("level")
    private String level;

    @Field("title")
    private String title;

    @Field("questions")
    private List<Question> questions;

    @Field("passing_count")
    private int passingCount;

    @Field("estimated_minutes")
    private int estimatedMinutes;

    @Field("cached_at")
    private LocalDateTime cachedAt;

    @Field("expires_at")
    private LocalDateTime expiresAt;

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @Builder
    @AllArgsConstructor
    public static class Question {

        @Field("id")
        private String id;

        @Field("question")
        private String question;

        @Field("options")
        private List<Option> options;

        @Field("correct_option_id")
        private String correctOptionId;

        @Field("explanation")
        private String explanation;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @Builder
    @AllArgsConstructor
    public static class Option {

        @Field("id")
        private String id;

        @Field("text")
        private String text;
    }
}
