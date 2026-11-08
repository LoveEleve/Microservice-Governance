package com.wuba.crm.tx.tcc.api;

import java.nio.charset.StandardCharsets;

final class TccValidation {

    static final int MAX_APPLICATION_DATA_BYTES = 64 * 1024;

    private TccValidation() {
    }

    static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    static String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    static String applicationData(String value) {
        String data = value == null ? "" : value;
        if (data.getBytes(StandardCharsets.UTF_8).length > MAX_APPLICATION_DATA_BYTES) {
            throw new IllegalArgumentException("applicationData exceeds 64KB");
        }
        return data;
    }

    static long requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
