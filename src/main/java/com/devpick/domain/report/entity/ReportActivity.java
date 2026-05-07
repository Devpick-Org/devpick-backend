package com.devpick.domain.report.entity;

import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "report_activities", indexes = {
        @Index(name = "idx_report_activities_report_id", columnList = "report_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ReportActivity extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private WeeklyReport report;

    @Column(name = "contents_read", nullable = false)
    @Builder.Default
    private Integer contentsRead = 0;

    @Column(name = "questions_created", nullable = false)
    @Builder.Default
    private Integer questionsCreated = 0;

    @Column(name = "scraps_count", nullable = false)
    @Builder.Default
    private Integer scrapsCount = 0;

    @Column(name = "job_postings_viewed", nullable = false)
    @Builder.Default
    private Integer jobPostingsViewed = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_tech_stacks")
    private String jobTechStacks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_keywords")
    private String contentKeywords;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_analysis")
    private String questionAnalysis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_tags")
    private String topTags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prev_week_comparison")
    private String prevWeekComparison;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "daily_activities")
    private String dailyActivities;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tag_activities")
    private String tagActivities;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "highlights")
    private String highlights;
}
