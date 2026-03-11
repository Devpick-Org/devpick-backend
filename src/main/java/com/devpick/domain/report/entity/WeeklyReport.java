package com.devpick.domain.report.entity;

import com.devpick.domain.user.entity.User;
import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "weekly_reports", indexes = {
        @Index(name = "idx_weekly_reports_user_week", columnList = "user_id, week_start", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class WeeklyReport extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "week_end", nullable = false)
    private LocalDate weekEnd;

    @Column(name = "share_token", length = 100, unique = true)
    private String shareToken;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "generated";

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReportActivity> activities = new ArrayList<>();

    public void updateShareToken(String token) {
        this.shareToken = token;
    }
}
