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
public class AiQuizDocument {

    private String contentId;
    private String level;
    private String title;
    private List<Question> questions;
    private int passingCount;
    private int estimatedMinutes;
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

    @DynamoDbAttribute("title")
    public String getTitle() {
        return title;
    }

    @DynamoDbAttribute("questions")
    public List<Question> getQuestions() {
        return questions;
    }

    @DynamoDbAttribute("passing_count")
    public int getPassingCount() {
        return passingCount;
    }

    @DynamoDbAttribute("estimated_minutes")
    public int getEstimatedMinutes() {
        return estimatedMinutes;
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

    @DynamoDbBean
    @Getter
    @Setter
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor
    public static class Question {

        private String id;
        /** "multiple_choice" 또는 "short_answer" */
        private String type;
        private String question;
        private List<Option> options;
        private String correctOptionId;
        private String explanation;
        private String correctAnswer;

        @DynamoDbAttribute("id")
        public String getId() {
            return id;
        }

        @DynamoDbAttribute("type")
        public String getType() {
            return type;
        }

        @DynamoDbAttribute("question")
        public String getQuestion() {
            return question;
        }

        @DynamoDbAttribute("options")
        public List<Option> getOptions() {
            return options;
        }

        @DynamoDbAttribute("correct_option_id")
        public String getCorrectOptionId() {
            return correctOptionId;
        }

        @DynamoDbAttribute("explanation")
        public String getExplanation() {
            return explanation;
        }

        @DynamoDbAttribute("correct_answer")
        public String getCorrectAnswer() {
            return correctAnswer;
        }
    }

    @DynamoDbBean
    @Getter
    @Setter
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor
    public static class Option {

        private String id;
        private String text;

        @DynamoDbAttribute("id")
        public String getId() {
            return id;
        }

        @DynamoDbAttribute("text")
        public String getText() {
            return text;
        }
    }
}
