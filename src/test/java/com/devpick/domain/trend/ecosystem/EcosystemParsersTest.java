package com.devpick.domain.trend.ecosystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EcosystemParsersTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("__NEXT_DATA__에서 pageProps 내 courseList를 추출할 수 있다")
    void nextData_extractsBootcampCourseList() throws Exception {
        String html = """
                <!DOCTYPE html><html><body>
                <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"courseList":[{"id":1,"title":"Test Course","brand":"ACME","thumbnail":"a.png","recruitEndDate":"2026-12-01","classStartDate":"2026-12-10","classEndDate":"2027-01-10","classify":"웹","classMethod":"온라인","costType":"free","cost":0}]}}}</script>
                </body></html>
                """;
        var pageProps = NextDataPagePropsExtractor.extract(objectMapper, html);
        assertThat(pageProps).isPresent();
        assertThat(pageProps.get().path("courseList").isArray()).isTrue();
        assertThat(pageProps.get().path("courseList").get(0).path("title").asText()).isEqualTo("Test Course");
    }

    @Test
    @DisplayName("테카 Club 블록 파싱 시 개발 직군이 있는 항목만 반환한다")
    void teca_parsesClubWithDevFields() {
        String script = """
                const Club = {
                PURE_PM: { name: "PM 만", link: "https://example.com", dots: "🌕", recruitStart: "1월 1일 2026", recruitEnd: "1월 2일 2026", fields: [Field.PM, Field.MARKETING] },
                FULL_DEV: { name: "데브 클럽", link: "https://dev.example", recruitStart: "2월 1일 2026", recruitEnd: "2월 10일 2026", fields: [Field.PM, Field.WEB] }
                };
                const BootcampCost = { FREE: "무료" };
                """;
        var fetcher = new TecaClubEcosystemFetcher(null);
        var items = fetcher.parseClubBlock(script);
        assertThat(items.stream().map(EcosystemTrendItem::title)).containsExactly("데브 클럽");
        assertThat(items.get(0).category()).isEqualTo(EcosystemTrendCategory.CLUB);
    }
}
