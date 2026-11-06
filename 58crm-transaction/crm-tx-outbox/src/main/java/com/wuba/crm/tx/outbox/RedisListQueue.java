package com.wuba.crm.tx.outbox;

import java.time.Duration;

/**
 * 业务线 Redis List 的最小操作接口。
 *
 * <p>队列元素是 {@link QueueEnvelope}，不是消息正文的唯一存储。Redis 里的副本
 * 允许丢失，生产方 MySQL 中的 Outbox 才是可重放的依据。</p>
 */
public interface RedisListQueue {

    /**
     * 投递一条消息到业务线队列尾部。
     */
    void enqueue(String bizCode, QueueEnvelope envelope);

    /**
     * 从业务线队列头部阻塞取一条消息。
     *
     * @return 队列为空且超时返回 {@code null}
     */
    QueueEnvelope dequeue(String bizCode, Duration timeout);
}
