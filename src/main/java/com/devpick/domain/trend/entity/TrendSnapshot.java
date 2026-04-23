package com.devpick.domain.trend.entity;

import com.devpick.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "trend_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_trend_snapshots_unit_scope_period",
                columnNames = {"unit", "scope", "period_start"}
        ),
        indexes = @Index(name = "idx_trend_snapshots_lookup",
                columnList = "unit, scope, period_start")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class TrendSnapshot extends BaseCreatedEntity {

    @Column(length = 10, nullable = false)
    private String unit;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String scope = "global";

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
