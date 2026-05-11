package com.devpick.domain.trend.ecosystem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EcosystemTrendSortTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 11);

    @Test
    @DisplayName("진행 중·미래(종료일 오늘 이상 또는 미기재)를 지난 행사보다 위에 둔다")
    void notPast_before_past() {
        var pastEndedMay3 =
                sample("past-may3", "2026-04-01", "2026-05-03"); // TODAY 이전 종료 → past
        var stillOpen = sample("future-june", "2026-05-20", "2026-06-01");
        var noDates = sample("club-no-dates", null, null);

        List<EcosystemTrendItem> sorted =
                EcosystemTrendSort.sortedCopy(List.of(pastEndedMay3, stillOpen, noDates), TODAY);

        assertThat(sorted).extracting(EcosystemTrendItem::id).containsExactly("club-no-dates", "future-june", "past-may3");
    }

    @Test
    @DisplayName("미래 묶음: endAt 내림차순 후 startAt 내림차순, 지난 묶음: 가장 최근 종료 순")
    void ordering_within_buckets() {
        var pOld = sample("pe", "2026-01-01", "2026-04-01");
        var pRecent = sample("p-recent", "2026-03-01", "2026-05-03");
        var fLateStart = sample("flate", "2026-03-01", "2026-06-01");
        var fEarlyStart = sample("fearly", "2026-05-01", "2026-06-01");
        List<EcosystemTrendItem> sorted =
                EcosystemTrendSort.sortedCopy(List.of(pOld, fEarlyStart, pRecent, fLateStart), TODAY);

        assertThat(sorted)
                .extracting(EcosystemTrendItem::id)
                .containsExactly(
                        "fearly", // 종료 같은 6월 1일 묶음: 시작일 내림차로 5월 1일이 먼저
                        "flate",
                        "p-recent", // 종료 지난 항목: 가장 최근 종료가 위
                        "pe");
    }

    @Test
    @DisplayName("동일 우선순위일 때 id로 안정 정렬한다")
    void tie_break_by_id() {
        var b = sample("b", "2026-05-01", "2026-06-01");
        var a = sample("a", "2026-05-01", "2026-06-01");
        List<EcosystemTrendItem> sorted = EcosystemTrendSort.sortedCopy(List.of(b, a), TODAY);
        assertThat(sorted).extracting(EcosystemTrendItem::id).containsExactly("a", "b");
    }

    @Test
    @DisplayName("ISO 인스턴트 endAt도 서울 기준 일로 해석된다")
    void parses_iso_instant_end() {
        var item =
                new EcosystemTrendItem(
                        "iso",
                        EcosystemTrendCategory.EVENT,
                        "t",
                        "o",
                        null,
                        "https://ex",
                        null,
                        "2026-04-01T00:00:00Z",
                        "2026-05-03T15:00:00Z",
                        List.of(),
                        "src");
        assertThat(EcosystemTrendSort.isPast(item, TODAY)).isTrue();
        var future =
                itemWithEnd("iso2", "2026-06-01T02:00:00+09:00", "2026-07-31T02:00:00+09:00");
        assertThat(EcosystemTrendSort.isPast(future, TODAY)).isFalse();
    }

    private static EcosystemTrendItem itemWithEnd(String id, String start, String end) {
        return new EcosystemTrendItem(
                id, EcosystemTrendCategory.EVENT, id, "o", null, "x", null, start, end, List.of(), "s");
    }

    private static EcosystemTrendItem sample(String id, String start, String end) {
        return itemWithEnd(id, start, end);
    }
}
