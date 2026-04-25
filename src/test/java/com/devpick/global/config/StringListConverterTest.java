package com.devpick.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StringListConverterTest {

    private StringListConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StringListConverter();
    }

    @Test
    @DisplayName("리스트를 JSON 문자열로 직렬화한다")
    void convertToDatabaseColumn_validList_returnsJson() {
        String result = converter.convertToDatabaseColumn(List.of("Spring", "Java"));

        assertThat(result).isEqualTo("[\"Spring\",\"Java\"]");
    }

    @Test
    @DisplayName("null 입력 시 null을 반환한다")
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("빈 리스트 입력 시 null을 반환한다")
    void convertToDatabaseColumn_emptyList_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
    }

    @Test
    @DisplayName("JSON 문자열을 리스트로 역직렬화한다")
    void convertToEntityAttribute_validJson_returnsList() {
        List<String> result = converter.convertToEntityAttribute("[\"Spring\",\"Java\"]");

        assertThat(result).containsExactly("Spring", "Java");
    }

    @Test
    @DisplayName("null DB 값은 빈 리스트를 반환한다")
    void convertToEntityAttribute_null_returnsEmptyList() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    @DisplayName("빈 문자열 DB 값은 빈 리스트를 반환한다")
    void convertToEntityAttribute_blankString_returnsEmptyList() {
        assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    @DisplayName("잘못된 JSON은 빈 리스트를 반환한다")
    void convertToEntityAttribute_invalidJson_returnsEmptyList() {
        assertThat(converter.convertToEntityAttribute("not-json")).isEmpty();
    }

    @Test
    @DisplayName("직렬화 후 역직렬화하면 원본과 동일하다")
    void roundTrip_preservesData() {
        List<String> original = List.of("IoC", "DI", "Spring Boot");

        String json = converter.convertToDatabaseColumn(original);
        List<String> result = converter.convertToEntityAttribute(json);

        assertThat(result).isEqualTo(original);
    }
}
