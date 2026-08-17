package com.example.property.client;

import java.util.Map;

public final class InfraiException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    private final Map<String, Object> details;

    public InfraiException(String code, Map<String, Object> details, int httpStatus) {
        super(code + ": " + details);
        this.code = code;
        this.details = Map.copyOf(details);
        this.httpStatus = httpStatus;
    }

    public String code() { return code; }
    public int httpStatus() { return httpStatus; }
    public Map<String, Object> details() { return details; }
}
