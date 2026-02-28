package com.devpick.domain.community.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_questions", indexes = {
        @Index(name = "idx_ai_questions_post_id", columnList = "post_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AiQuestion {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "original_title", length = 500, nullable = false)
    private String originalTitle;

    @Column(name = "refined_title", length = 500, nullable = false)
    private String refinedTitle;

    @Column(name = "refined_content", columnDefinition = "TEXT")
    private String refinedContent;

    @Column(columnDefinition = "jsonb")
    private String suggestions; // JSONB: AI 개선 제안 목록

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
