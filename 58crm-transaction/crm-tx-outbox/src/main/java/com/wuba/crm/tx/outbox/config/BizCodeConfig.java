package com.wuba.crm.tx.outbox.config;

/**
 * 一个业务线的消息配置。
 *
 * @param bizCode 业务编码，例如 lead/quota/contract
 * @param consumers 消费并发；生产端暂不使用，消费端启动时使用
 * @param batchSize 单批拉取/发送上限
 */
public record BizCodeConfig(String bizCode, int consumers, int batchSize) {

    public BizCodeConfig {
        if (bizCode == null || bizCode.isBlank()) {
            throw new IllegalArgumentException("bizCode must not be blank");
        }
        if (consumers <= 0) {
            throw new IllegalArgumentException("consumers must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }

    public BizCodeConfig(String bizCode) {
        this(bizCode, 1, 500);
    }
}
