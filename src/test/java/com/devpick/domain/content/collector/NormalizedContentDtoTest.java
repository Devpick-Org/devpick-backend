package com.devpick.domain.content.collector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizedContentDtoTest {

    private NormalizedContentDto buildDto(String publishedAt) {
        return new NormalizedContentDto(
                "techblog", "제목", "https://example.com/1",
                publishedAt, "미리보기", "본문", "rss", "full_body", null
        );
    }

    @Test
    @DisplayName("ISO 8601 Z 형식 파싱 성공")
    void parsedPublishedAt_utcZFormat_success() {
        NormalizedContentDto dto = buildDto("2026-03-10T09:00:00Z");

        LocalDateTime result = dto.parsedPublishedAt();

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 3, 10, 9, 0, 0));
    }

    @Test
    @DisplayName("ISO 8601 offset 형식 파싱 성공")
    void parsedPublishedAt_offsetFormat_success() {
        NormalizedContentDto dto = buildDto("2026-03-10T18:00:00+09:00");

        LocalDateTime result = dto.parsedPublishedAt();

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 3, 10, 18, 0, 0));
    }

    @Test
    @DisplayName("LocalDateTime 형식(오프셋 없음) 파싱 성공")
    void parsedPublishedAt_localDateTimeFormat_success() {
        NormalizedContentDto dto = buildDto("2026-03-10T09:00:00");

        LocalDateTime result = dto.parsedPublishedAt();

        assertThat(result).isEqualTo(LocalDateTime.of(2026, 3, 10, 9, 0, 0));
    }

    @Test
    @DisplayName("null publishedAt → null 반환")
    void parsedPublishedAt_null_returnsNull() {
        NormalizedContentDto dto = buildDto(null);

        assertThat(dto.parsedPublishedAt()).isNull();
    }

    @Test
    @DisplayName("blank publishedAt → null 반환")
    void parsedPublishedAt_blank_returnsNull() {
        NormalizedContentDto dto = buildDto("   ");

        assertThat(dto.parsedPublishedAt()).isNull();
    }

    @Test
    @DisplayName("파싱 불가 문자열 → null 반환")
    void parsedPublishedAt_invalidFormat_returnsNull() {
        NormalizedContentDto dto = buildDto("not-a-date");

        assertThat(dto.parsedPublishedAt()).isNull();
    }
}
