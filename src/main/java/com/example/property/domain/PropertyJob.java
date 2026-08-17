package com.example.property.domain;

import java.util.Map;

public record PropertyJob(String type, String propertyId, String subjectId, boolean urgent) {
    public static PropertyJob fromPayload(Map<String, Object> payload) {
        return new PropertyJob(
                String.valueOf(payload.get("type")),
                String.valueOf(payload.get("propertyId")),
                String.valueOf(payload.get("subjectId")),
                Boolean.TRUE.equals(payload.get("urgent")));
    }
}
