package com.devpick.domain.trend.ecosystem;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 생태계 트렌드 피드 정렬 — {@link EcosystemTrendService#getPage}에서 카테고리·검색 필터 후 일괄 적용.
 *
 * <p>규칙: (1) 종료일({@code endAt})이 오늘(ASIA/SEOUL 자정 기준) 이전인 항목은 "지남"으로 묶어 아래로, {@code endAt}
 * 공백·미파싱은 미래로 간주해 위로 유지 — (2) 미래 구간에서는 마감/종료일 최신 순({@code endAt} 내림차순), 같은
 * 줄은 시작일 최신 순({@code startAt} 내림차순)으로 최근 시작·먼 마감이 상단에 — (3) 지남 구간은 가장 최근에
 * 끝난 항목부터({@code endAt} 내림차순) — (4) 동률 시 {@code id}, {@code title} 안정 정렬.
 */
final class EcosystemTrendSort {

    static final ZoneId FEED_ZONE = ZoneId.of("Asia/Seoul");

    private EcosystemTrendSort() {}

    static List<EcosystemTrendItem> sortedCopy(List<EcosystemTrendItem> items, LocalDate today) {
        List<EcosystemTrendItem> copy = new ArrayList<>(items);
        copy.sort(comparator(today));
        return copy;
    }

    static Comparator<EcosystemTrendItem> comparator(LocalDate today) {
        return (a, b) -> {
            boolean pa = isPast(a, today);
            boolean pb = isPast(b, today);
            int c = Boolean.compare(pa, pb);
            if (c != 0) {
                return c;
            }
            c = Long.compare(endEpochDescendingKey(b, pb), endEpochDescendingKey(a, pa));
            if (c != 0) {
                return c;
            }
            if (!pa) {
                c = Long.compare(startEpochDescendingKey(b), startEpochDescendingKey(a));
                if (c != 0) {
                    return c;
                }
            }
            c = Comparator.nullsFirst(String::compareTo).compare(a.id(), b.id());
            if (c != 0) {
                return c;
            }
            String ta = a.title() == null ? null : a.title();
            String tb = b.title() == null ? null : b.title();
            return Comparator.nullsFirst(String::compareTo).compare(ta, tb);
        };
    }

    /** {@code endAt} 기준 서울 로컬일이 오늘보다 이전이면 지난 항목. */
    static boolean isPast(EcosystemTrendItem item, LocalDate today) {
        Optional<LocalDate> end = parseFlexibleDate(item.endAt());
        return end.filter(d -> d.isBefore(today)).isPresent();
    }

    /**
     * 내림차순 정렬용 epoch day 키. 미래 묶음에서 {@code endAt} 없음 → 맨 위로(MAX), 지난 묶음에서 파싱 실패 →
     * 아래로(MIN).
     */
    private static long endEpochDescendingKey(EcosystemTrendItem item, boolean past) {
        Optional<LocalDate> end = parseFlexibleDate(item.endAt());
        if (past) {
            return end.map(LocalDate::toEpochDay).orElse(Long.MIN_VALUE);
        }
        return end.map(LocalDate::toEpochDay).orElse(Long.MAX_VALUE);
    }

    private static long startEpochDescendingKey(EcosystemTrendItem item) {
        return parseFlexibleDate(item.startAt()).map(LocalDate::toEpochDay).orElse(Long.MIN_VALUE);
    }

    /**
     * {@code yyyy-MM-dd}, ISO 인스턴트/오프셋 등을 서울 달력 일로 해석합니다. 형식 불명은 empty.
     */
    static Optional<LocalDate> parseFlexibleDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = raw.strip();
        try {
            return Optional.of(LocalDate.parse(s));
        } catch (DateTimeParseException ignored) {
            // ISO date only failed; try instants below
        }
        try {
            return Optional.of(Instant.parse(s).atZone(FEED_ZONE).toLocalDate());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Optional.of(
                    OffsetDateTime.parse(s).atZoneSameInstant(FEED_ZONE).toLocalDate());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Optional.of(ZonedDateTime.parse(s).withZoneSameInstant(FEED_ZONE).toLocalDate());
        } catch (DateTimeParseException ignored) {
        }
        return Optional.empty();
    }
}
