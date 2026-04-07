package com.devpick.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    private JacksonConfig jacksonConfig;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jacksonConfig = new JacksonConfig();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        jacksonConfig.jacksonCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    @Test
    @DisplayName("Instant이 UTC Z 포함 ISO 8601 형식으로 직렬화된다")
    void instant_직렬화_Z포함() throws Exception {
        Instant instant = Instant.parse("2026-03-21T10:00:00Z");

        String result = objectMapper.writeValueAsString(instant);

        assertThat(result).isEqualTo("\"2026-03-21T10:00:00Z\"");
    }

    @Test
    @DisplayName("Instant이 타임스탬프(숫자)가 아닌 문자열로 직렬화된다")
    void instant_타임스탬프_비활성화() throws Exception {
        Instant instant = Instant.parse("2026-03-21T10:00:00Z");

        String result = objectMapper.writeValueAsString(instant);

        assertThat(result).startsWith("\"");
        assertThat(objectMapper.getSerializationConfig()
                .isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
    }

    @Test
    @DisplayName("Instant이 나노초 없이 Z suffix ISO 8601 형식으로 직렬화된다")
    void instant_나노초없이_직렬화() throws Exception {
        Instant instant = Instant.ofEpochSecond(1742551200L); // 2026-03-21T10:00:00Z

        String result = objectMapper.writeValueAsString(instant);

        assertThat(result).endsWith("Z\"");
        assertThat(result).startsWith("\"");
    }

    @Test
    @DisplayName("jacksonCustomizer 빈이 생성된다")
    void jacksonCustomizer_빈_생성() {
        assertThat(jacksonConfig.jacksonCustomizer()).isNotNull();
    }
}