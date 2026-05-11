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
 * <p>규칙: (1) 종료일({@code endAt})이 내일(ASIA/SEOUL) 이후가 아니면(오늘·과거 포함) “지남” 묶음으로 아래에 둠.
 * 파싱 실패·공백은 미래처럼 유지해 위쪽 — (2) 미래(종료가 내일 이후) 구간에서는 {@code endAt} 내림차순 후 {@code startAt}
 * 내림차순 — (3) 지남 묶음은 가장 최근에 끝난 순({@code endAt} 내림차순) — (4) 동률 시 {@code id}, {@code title}.
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

    /** {@code endAt} 기준 서울 로컬일이 내일(ASIA/SEOUL) 이후가 아니면 "지남"(오늘 종료 포함). 미파싱은 지난 아님. */
    static boolean isPast(EcosystemTrendItem item, LocalDate today) {
        Optional<LocalDate> end = parseFlexibleDate(item.endAt());
        if (end.isEmpty()) {
            return false;
        }
        LocalDate e = end.get();
        return !e.isAfter(today);
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
