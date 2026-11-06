package com.wuba.crm.tx.outbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
     * 批量投递。默认实现逐条降级，生产适配器应覆盖为 pipeline。
     */
    default void enqueueBatch(String bizCode, List<QueueEnvelope> envelopes) {
        for (QueueEnvelope envelope : envelopes) {
            enqueue(bizCode, envelope);
        }
    }

    /**
     * 从业务线队列头部阻塞取一条消息。
     *
     * @return 队列为空且超时返回 {@code null}
     */
    QueueEnvelope dequeue(String bizCode, Duration timeout);

    /**
     * 批量拉取。先非阻塞批量取，空队列时阻塞等待第一条，再补取剩余。
     */
    default List<QueueEnvelope> dequeueBatch(String bizCode, int maxCount, Duration timeout) {
        List<QueueEnvelope> result = new ArrayList<>();
        QueueEnvelope first = dequeue(bizCode, timeout);
        if (first != null) {
            result.add(first);
        }
        while (result.size() < maxCount) {
            QueueEnvelope next = dequeue(bizCode, Duration.ZERO);
            if (next == null) {
                break;
            }
            result.add(next);
        }
        return result;
    }
}
