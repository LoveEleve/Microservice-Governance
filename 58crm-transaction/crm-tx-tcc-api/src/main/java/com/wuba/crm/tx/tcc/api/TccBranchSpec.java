package com.wuba.crm.tx.tcc.api;

import java.util.Objects;

/**
 * 一个 TCC 分支的公共描述。
 *
 * @param resourceKey      分支幂等键
 * @param actionName       TCC 动作名
 * @param callbackEndpoint TC 回调 RM 的 gRPC 地址，格式 host:port
 * @param applicationData  Try/Confirm/Cancel 需要的业务上下文
 * @param tryAction        业务 Try 回调
 */
public record TccBranchSpec(
        String resourceKey,
        String actionName,
        String callbackEndpoint,
        String applicationData,
        TccTryAction tryAction) {

    public TccBranchSpec {
        resourceKey = TccValidation.requireText(resourceKey, "resourceKey", 128);
        actionName = TccValidation.requireText(actionName, "actionName", 128);
        callbackEndpoint = TccValidation.requireText(callbackEndpoint, "callbackEndpoint", 256);
        applicationData = TccValidation.applicationData(applicationData);
        tryAction = Objects.requireNonNull(tryAction, "tryAction");
    }

    /**
     * 创建 branch builder。
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * TccBranchSpec builder。
     */
    public static final class Builder {

        private String resourceKey;
        private String actionName;
        private String callbackEndpoint;
        private String applicationData = "";
        private TccTryAction tryAction;

        /**
         * @param value 分支幂等键
         * @return this
         */
        public Builder resourceKey(String value) {
            this.resourceKey = value;
            return this;
        }

        /**
         * @param value TCC 动作名
         * @return this
         */
        public Builder actionName(String value) {
            this.actionName = value;
            return this;
        }

        /**
         * @param value TC 回调 gRPC 地址
         * @return this
         */
        public Builder callbackEndpoint(String value) {
            this.callbackEndpoint = value;
            return this;
        }

        /**
         * @param value 业务上下文
         * @return this
         */
        public Builder applicationData(String value) {
            this.applicationData = value;
            return this;
        }

        /**
         * @param value 业务 Try 回调
         * @return this
         */
        public Builder tryAction(TccTryAction value) {
            this.tryAction = value;
            return this;
        }

        /**
         * @return 不可变分支
         */
        public TccBranchSpec build() {
            return new TccBranchSpec(
                    resourceKey, actionName, callbackEndpoint, applicationData, tryAction);
        }
    }
}
