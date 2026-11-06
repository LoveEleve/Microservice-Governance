package com.wuba.crm.tx.outbox;

/**
 * 发送端拦截器，对应 Kafka {@code ProducerInterceptor}。
 *
 * <p>用于 traceId、租户、schema 版本、加密、采样、审计和指标等横切能力。
 * 拦截器异常只记录，不打断发送。</p>
 */
public interface OutboxInterceptor {

    /**
     * 在序列化和写入 Stream 前调用；可以返回新的 record。
     */
    OutboxRecord onSend(OutboxRecord record);

    /**
     * 在一次 Stream 写入成功或失败后调用。
     */
    void onAcknowledgement(OutboxSendResult result, Throwable error);

    /**
     * 关闭资源。
     */
    default void close() {
    }
}
