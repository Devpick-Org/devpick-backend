package com.devpick.global.config;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class LocalDateTimeConverter implements AttributeConverter<LocalDateTime> {

    /**
     * DynamoDB/파이프라인에 따라 ISO 문자열 형태가 다름.
     * - {@code 2026-04-13T04:09:38.960113938} (오프셋 없음)
     * - {@code 2026-04-20T15:00:39.550585+00:00} (오프셋 있음) — {@link LocalDateTime#parse} 단독으로는 실패함
     */
    static LocalDateTime parseStoredDateTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeException ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(s).toLocalDateTime();
        } catch (DateTimeException ignored) {
            // fall through
        }
        return Instant.parse(s).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    @Override
    public AttributeValue transformFrom(LocalDateTime input) {
        if (input == null) {
            return AttributeValue.fromNul(true);
        }
        return AttributeValue.fromS(input.toString());
    }

    @Override
    public LocalDateTime transformTo(AttributeValue input) {
        if (input.s() == null) {
            return null;
        }
        return parseStoredDateTime(input.s());
    }

    @Override
    public EnhancedType<LocalDateTime> type() {
        return EnhancedType.of(LocalDateTime.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
