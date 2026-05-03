package com.devpick.domain.trend.ecosystem;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EcosystemTrendCategory {
    BOOTCAMP("bootcamp"),
    CLUB("club"),
    EVENT("event");

    private final String jsonValue;

    EcosystemTrendCategory(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }

    public static EcosystemTrendCategory fromParam(String raw) {
        return fromString(raw);
    }

    @JsonCreator
    public static EcosystemTrendCategory fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (EcosystemTrendCategory c : values()) {
            if (c.jsonValue.equalsIgnoreCase(raw)) {
                return c;
            }
        }
        return null;
    }
}
