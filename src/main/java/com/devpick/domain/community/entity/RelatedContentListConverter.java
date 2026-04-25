package com.devpick.domain.community.entity;

import com.devpick.domain.community.dto.RelatedContentItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Converter
public class RelatedContentListConverter implements AttributeConverter<List<RelatedContentItem>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<List<RelatedContentItem>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<RelatedContentItem> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize List<RelatedContentItem>: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public List<RelatedContentItem> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return List.of();
        try {
            return objectMapper.readValue(dbData, TYPE_REF);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize List<RelatedContentItem>: {}", e.getMessage());
            return List.of();
        }
    }
}
