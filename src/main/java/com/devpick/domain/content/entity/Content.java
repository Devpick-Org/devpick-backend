package com.devpick.domain.content.entity;

import com.devpick.domain.content.dto.StackOverflowAnswerDto;
import com.devpick.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contents", indexes = {
        @Index(name = "idx_contents_canonical_url", columnList = "canonical_url"),
        @Index(name = "idx_contents_source_id", columnList = "source_id"),
        @Index(name = "idx_contents_is_available", columnList = "is_available"),
        @Index(name = "idx_contents_published_at", columnList = "published_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Content extends BaseTimeEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private ContentSource source;

    @Column(length = 500, nullable = false)
    private String title;

    @Column(name = "translated_title", length = 500)
    private String translatedTitle;

    @Column(length = 100)
    private String author;

    @Column(name = "canonical_url", length = 1000, nullable = false, unique = true)
    private String canonicalUrl;

    @Column(columnDefinition = "TEXT")
    private String preview;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "thumbnail_width")
    private Integer thumbnailWidth;

    @Column(name = "thumbnail_height")
    private Integer thumbnailHeight;

    @Column(name = "is_original_visible", nullable = false)
    @Builder.Default
    private Boolean isOriginalVisible = false;

    @Column(name = "license_type", length = 50)
    private String licenseType;

    @Column(name = "original_content", columnDefinition = "TEXT")
    private String originalContent;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @Column(name = "takedown_requested_at")
    private LocalDateTime takedownRequestedAt;

    // AI 요약 완료 후 저장되는 분류 정보 (AI 레포 save_ai_metadata()가 직접 UPDATE)
    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;     // JSON 배열 문자열 예: ["Spring Boot","JPA"]

    @Column(name = "category", length = 100)
    private String category;

    // 소스별 참여 지표 (null for RSS sources)
    @Column(name = "score")
    private Integer score;         // SO 전용: 추천 순점수 (upvote - downvote, 음수 가능)

    @Column(name = "likes")
    private Integer likes;         // Velog 전용: 좋아요 수

    @Column(name = "view_count")
    private Integer viewCount;     // SO 전용: 조회수

    @Column(name = "comments_count")
    private Integer commentsCount; // Velog 전용: 댓글 수

    // Stack Overflow 전용 구조화 필드

    @Column(name = "is_answered")
    private Boolean isAnswered;

    @Column(name = "question_content", columnDefinition = "TEXT")
    private String questionContent;

    @Column(name = "accepted_answer", columnDefinition = "TEXT")
    @Convert(converter = StackOverflowAnswerConverter.class)
    private StackOverflowAnswerDto acceptedAnswer;

    @Column(name = "top_answers", columnDefinition = "TEXT")
    @Convert(converter = StackOverflowAnswerListConverter.class)
    private List<StackOverflowAnswerDto> topAnswers;

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ContentTag> contentTags = new ArrayList<>();
}
