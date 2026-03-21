package com.devpick.domain.content.entity;

import com.devpick.domain.content.dto.StackOverflowAnswerDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * StackOverflowAnswerDto ↔ JSON String (TEXT 컬럼) JPA 변환기.
 * acceptedAnswer 단일 객체 저장에 사용된다.
 */
@Slf4j
@Converter
public class StackOverflowAnswerConverter implements AttributeConverter<StackOverflowAnswerDto, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(StackOverflowAnswerDto attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize StackOverflowAnswerDto: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public StackOverflowAnswerDto convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, StackOverflowAnswerDto.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize StackOverflowAnswerDto: {}", e.getMessage());
            return null;
        }
    }
}