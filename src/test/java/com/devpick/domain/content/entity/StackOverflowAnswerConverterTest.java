package com.devpick.domain.content.entity;

import com.devpick.domain.content.dto.StackOverflowAnswerDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StackOverflowAnswerConverterTest {

    private final StackOverflowAnswerConverter converter = new StackOverflowAnswerConverter();

    @Test
    @DisplayName("convertToDatabaseColumn — DTO를 JSON 문자열로 직렬화")
    void convertToDatabaseColumn_returnsJsonString() {
        StackOverflowAnswerDto dto = new StackOverflowAnswerDto("This is the body", 42);

        String result = converter.convertToDatabaseColumn(dto);

        assertThat(result).contains("\"body\":\"This is the body\"");
        assertThat(result).contains("\"score\":42");
    }

    @Test
    @DisplayName("convertToDatabaseColumn — null 입력 시 null 반환")
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("convertToEntityAttribute — JSON 문자열을 DTO로 역직렬화")
    void convertToEntityAttribute_returnsDto() {
        String json = "{\"body\":\"answer text\",\"score\":15}";

        StackOverflowAnswerDto result = converter.convertToEntityAttribute(json);

        assertThat(result).isNotNull();
        assertThat(result.body()).isEqualTo("answer text");
        assertThat(result.score()).isEqualTo(15);
    }

    @Test
    @DisplayName("convertToEntityAttribute — null 입력 시 null 반환")
    void convertToEntityAttribute_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("convertToEntityAttribute — 빈 문자열 시 null 반환")
    void convertToEntityAttribute_blank_returnsNull() {
        assertThat(converter.convertToEntityAttribute("   ")).isNull();
    }

    @Test
    @DisplayName("직렬화 후 역직렬화 — 동일한 DTO 반환 (round-trip)")
    void roundTrip_preservesValues() {
        StackOverflowAnswerDto original = new StackOverflowAnswerDto("round trip body", 99);

        String serialized = converter.convertToDatabaseColumn(original);
        StackOverflowAnswerDto restored = converter.convertToEntityAttribute(serialized);

        assertThat(restored.body()).isEqualTo(original.body());
        assertThat(restored.score()).isEqualTo(original.score());
    }
}