package com.devpick.domain.content.entity;

import com.devpick.domain.content.dto.StackOverflowAnswerDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * List&lt;StackOverflowAnswerDto&gt; ↔ JSON String (TEXT 컬럼) JPA 변환기.
 * topAnswers 리스트 저장에 사용된다.
 */
@Slf4j
@Converter
public class StackOverflowAnswerListConverter implements AttributeConverter<List<StackOverflowAnswerDto>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<List<StackOverflowAnswerDto>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<StackOverflowAnswerDto> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize List<StackOverflowAnswerDto>: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<StackOverflowAnswerDto> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, TYPE_REF);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize List<StackOverflowAnswerDto>: {}", e.getMessage());
            return null;
        }
    }
}