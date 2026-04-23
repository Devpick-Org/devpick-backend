package com.devpick.domain.trend.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TrendSnapshotTest {

    @Test
    @DisplayName("scope 기본값은 global이다")
    void builder_defaultScope_isGlobal() {
        TrendSnapshot snapshot = TrendSnapshot.builder()
                .unit("daily")
                .periodStart(LocalDate.now())
                .periodEnd(LocalDate.now())
                .payload("{}")
                .generatedAt(LocalDateTime.now())
                .build();

        assertThat(snapshot.getScope()).isEqualTo("global");
    }

    @Test
    @DisplayName("모든 필드가 정상적으로 설정된다")
    void builder_allFields_assigned() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 7);
        LocalDateTime generatedAt = LocalDateTime.of(2026, 4, 7, 0, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 6, 0, 0);

        TrendSnapshot snapshot = TrendSnapshot.builder()
                .unit("weekly")
                .scope("global")
                .periodStart(start)
                .periodEnd(end)
                .payload("{\"top_tags\":[]}")
                .generatedAt(generatedAt)
                .expiresAt(expiresAt)
                .build();

        assertThat(snapshot.getUnit()).isEqualTo("weekly");
        assertThat(snapshot.getScope()).isEqualTo("global");
        assertThat(snapshot.getPeriodStart()).isEqualTo(start);
        assertThat(snapshot.getPeriodEnd()).isEqualTo(end);
        assertThat(snapshot.getPayload()).isEqualTo("{\"top_tags\":[]}");
        assertThat(snapshot.getGeneratedAt()).isEqualTo(generatedAt);
        assertThat(snapshot.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("expiresAt은 null을 허용한다")
    void expiresAt_nullable() {
        TrendSnapshot snapshot = TrendSnapshot.builder()
                .unit("monthly")
                .periodStart(LocalDate.now())
                .periodEnd(LocalDate.now())
                .payload("{}")
                .generatedAt(LocalDateTime.now())
                .build();

        assertThat(snapshot.getExpiresAt()).isNull();
    }
}
