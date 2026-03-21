package com.devpick.domain.content.entity;

import com.devpick.domain.content.dto.StackOverflowAnswerDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StackOverflowAnswerListConverterTest {

    private final StackOverflowAnswerListConverter converter = new StackOverflowAnswerListConverter();

    @Test
    @DisplayName("convertToDatabaseColumn — List를 JSON 배열로 직렬화")
    void convertToDatabaseColumn_returnsJsonArray() {
        List<StackOverflowAnswerDto> list = List.of(
                new StackOverflowAnswerDto("body1", 10),
                new StackOverflowAnswerDto("body2", 5)
        );

        String result = converter.convertToDatabaseColumn(list);

        assertThat(result).startsWith("[");
        assertThat(result).contains("body1");
        assertThat(result).contains("body2");
    }

    @Test
    @DisplayName("convertToDatabaseColumn — null 입력 시 null 반환")
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("convertToDatabaseColumn — 빈 리스트 시 null 반환")
    void convertToDatabaseColumn_emptyList_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
    }

    @Test
    @DisplayName("convertToEntityAttribute — JSON 배열을 List로 역직렬화")
    void convertToEntityAttribute_returnsList() {
        String json = "[{\"body\":\"first\",\"score\":20},{\"body\":\"second\",\"score\":8}]";

        List<StackOverflowAnswerDto> result = converter.convertToEntityAttribute(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).body()).isEqualTo("first");
        assertThat(result.get(0).score()).isEqualTo(20);
        assertThat(result.get(1).body()).isEqualTo("second");
        assertThat(result.get(1).score()).isEqualTo(8);
    }

    @Test
    @DisplayName("convertToEntityAttribute — null 입력 시 null 반환")
    void convertToEntityAttribute_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("convertToEntityAttribute — 빈 문자열 시 null 반환")
    void convertToEntityAttribute_blank_returnsNull() {
        assertThat(converter.convertToEntityAttribute("  ")).isNull();
    }

    @Test
    @DisplayName("직렬화 후 역직렬화 — 동일한 리스트 반환 (round-trip)")
    void roundTrip_preservesList() {
        List<StackOverflowAnswerDto> original = List.of(
                new StackOverflowAnswerDto("alpha", 7),
                new StackOverflowAnswerDto("beta", 3)
        );

        String serialized = converter.convertToDatabaseColumn(original);
        List<StackOverflowAnswerDto> restored = converter.convertToEntityAttribute(serialized);

        assertThat(restored).hasSize(2);
        assertThat(restored.get(0).body()).isEqualTo("alpha");
        assertThat(restored.get(0).score()).isEqualTo(7);
        assertThat(restored.get(1).body()).isEqualTo("beta");
        assertThat(restored.get(1).score()).isEqualTo(3);
    }
}