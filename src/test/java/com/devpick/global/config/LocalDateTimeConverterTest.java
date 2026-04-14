package com.devpick.global.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDateTimeConverterTest {

    @Test
    void parseStoredDateTime_plainLocal() {
        LocalDateTime t = LocalDateTimeConverter.parseStoredDateTime("2026-04-13T04:09:38.960113938");
        assertThat(t).isEqualTo(LocalDateTime.parse("2026-04-13T04:09:38.960113938"));
    }

    @Test
    void parseStoredDateTime_withOffset_aiPipelineStyle() {
        LocalDateTime t = LocalDateTimeConverter.parseStoredDateTime("2026-04-20T15:00:39.550585+00:00");
        assertThat(t).isEqualTo(OffsetDateTime.parse("2026-04-20T15:00:39.550585+00:00").toLocalDateTime());
    }
}
