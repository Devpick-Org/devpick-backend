package com.devpick.domain.trend.ecosystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DevEventEcosystemFetcherTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("이벤트 id는 숫자·문자열 모두 파싱한다")
    void parseEventId_acceptsNumberAndText() throws Exception {
        assertThat(DevEventEcosystemFetcher.parseEventId(mapper.readTree("2932"))).isEqualTo(2932);
        assertThat(DevEventEcosystemFetcher.parseEventId(mapper.readTree("\"2990\""))).isEqualTo(2990);
        assertThat(DevEventEcosystemFetcher.parseEventId(mapper.readTree("null"))).isEqualTo(0);
    }

    @Test
    @DisplayName("fallbackData 에서 월별 dev_event 건수를 센다")
    void countRawEvents_sumsDevEventArrays() throws Exception {
        String json =
                """
                [{"dev_event":[{"id":1}]},{"metadata":{}},{"dev_event":[{"id":2},{"id":3}]}]
                """;
        assertThat(DevEventEcosystemFetcher.countRawEvents(mapper.readTree(json))).isEqualTo(3);
    }
}
