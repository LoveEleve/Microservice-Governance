package com.wuba.crm.tx.tcc.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 业务方发起一次 TCC 的完整请求。
 *
 * @param requestId  业务幂等键
 * @param bizCode    业务域
 * @param bizId      业务单号
 * @param tryTimeout Try 阶段超时
 * @param branches   分支列表
 */
public record TccRequest(
        String requestId,
        String bizCode,
        String bizId,
        Duration tryTimeout,
        List<TccBranch> branches) {

    public TccRequest {
        requestId = TccValidation.requireText(requestId, "requestId", 128);
        bizCode = TccValidation.requireText(bizCode, "bizCode", 64);
        bizId = TccValidation.requireText(bizId, "bizId", 128);
        tryTimeout = Objects.requireNonNull(tryTimeout, "tryTimeout");
        if (tryTimeout.isZero() || tryTimeout.isNegative()) {
            throw new IllegalArgumentException("tryTimeout must be positive");
        }
        branches = List.copyOf(Objects.requireNonNull(branches, "branches"));
        if (branches.isEmpty()) {
            throw new IllegalArgumentException("branches must not be empty");
        }
    }

    /**
     * 创建 request builder。
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * TccRequest builder。
     */
    public static final class Builder {

        private String requestId;
        private String bizCode;
        private String bizId;
        private Duration tryTimeout = Duration.ofSeconds(30);
        private final java.util.List<TccBranch> branches = new java.util.ArrayList<>();

        /**
         * @param value 业务幂等键
         * @return this
         */
        public Builder requestId(String value) {
            this.requestId = value;
            return this;
        }

        /**
         * @param value 业务域
         * @return this
         */
        public Builder bizCode(String value) {
            this.bizCode = value;
            return this;
        }

        /**
         * @param value 业务单号
         * @return this
         */
        public Builder bizId(String value) {
            this.bizId = value;
            return this;
        }

        /**
         * @param value Try 超时
         * @return this
         */
        public Builder tryTimeout(Duration value) {
            this.tryTimeout = value;
            return this;
        }

        /**
         * @param value 分支描述
         * @return this
         */
        public Builder branch(TccBranch value) {
            this.branches.add(value);
            return this;
        }

        /**
         * @return 不可变请求
         */
        public TccRequest build() {
            return new TccRequest(requestId, bizCode, bizId, tryTimeout, branches);
        }
    }
}
