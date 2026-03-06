package com.devpick.domain.report.entity;

import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "top_tags", columnDefinition = "jsonb")
    private String topTags;

    @Column(name = "prev_week_comparison", columnDefinition = "jsonb")
    private String prevWeekComparison;
}
