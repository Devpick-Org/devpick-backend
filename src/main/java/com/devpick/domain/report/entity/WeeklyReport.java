package com.devpick.domain.report.entity;

import com.devpick.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "weekly_reports", indexes = {
        @Index(name = "idx_weekly_reports_user_id", columnList = "user_id"),
        @Index(name = "idx_weekly_reports_week_start", columnList = "week_start")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class WeeklyReport {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "week_end", nullable = false)
    private LocalDate weekEnd;

    @Column(name = "contents_read", nullable = false)
    @Builder.Default
    private Integer contentsRead = 0;

    @Column(name = "questions_created", nullable = false)
    @Builder.Default
    private Integer questionsCreated = 0;

    @Column(name = "scraps_count", nullable = false)
    @Builder.Default
    private Integer scrapsCount = 0;

    @Column(name = "top_tags", columnDefinition = "jsonb")
    private String topTags; // JSONB: 많이 본 태그 TOP3

    @Column(name = "prev_week_comparison", columnDefinition = "jsonb")
    private String prevWeekComparison; // JSONB: 전주 대비 비교 데이터

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
