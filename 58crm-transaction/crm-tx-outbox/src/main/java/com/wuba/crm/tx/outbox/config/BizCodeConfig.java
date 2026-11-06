package com.wuba.crm.tx.outbox.config;

/**
 * 生产端业务线白名单配置。
 *
 * <p>这里只描述“这个 bizCode 是否允许发布”，不包含消费并发/消费批次。
 * 消费端参数属于 ConsumerConfig，不能混进生产端 Registry。</p>
 *
 * @param bizCode 业务编码，例如 lead/quota/contract
 */
public record BizCodeConfig(String bizCode) {

    public BizCodeConfig {
        if (bizCode == null || bizCode.isBlank()) {
            throw new IllegalArgumentException("bizCode must not be blank");
        }
    }
}
