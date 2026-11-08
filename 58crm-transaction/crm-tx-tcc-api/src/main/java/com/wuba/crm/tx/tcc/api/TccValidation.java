package com.wuba.crm.tx.tcc.api;

import java.nio.charset.StandardCharsets;

/**
 * TCC DTO 的公共参数校验。
 *
 * <p>所有校验失败都抛 {@link IllegalArgumentException}；服务端在 gRPC
 * 边界应把它转换成明确的参数错误，而不是 500。</p>
 */
final class TccValidation {

    static final int MAX_APPLICATION_DATA_BYTES = 64 * 1024;

    private TccValidation() {
    }

    /**
     * 校验必填文本。
     *
     * @param value     原值
     * @param field     字段名
     * @param maxLength 最大字符数
     * @return 原值
     */
    static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    /**
     * 校验可选文本，null 归一化为空串。
     *
     * @param value     原值
     * @param field     字段名
     * @param maxLength 最大字符数
     * @return 归一化后的文本
     */
    static String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return value;
    }

    /**
     * 校验业务上下文，限制 UTF-8 字节数不超过 64KB。
     *
     * @param value 业务上下文
     * @return 归一化后的业务上下文
     */
    static String applicationData(String value) {
        String data = value == null ? "" : value;
        if (data.getBytes(StandardCharsets.UTF_8).length > MAX_APPLICATION_DATA_BYTES) {
            throw new IllegalArgumentException("applicationData exceeds 64KB");
        }
        return data;
    }

    /**
     * 校验正整数。
     *
     * @param value 原值
     * @param field 字段名
     * @return 原值
     */
    static long requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
