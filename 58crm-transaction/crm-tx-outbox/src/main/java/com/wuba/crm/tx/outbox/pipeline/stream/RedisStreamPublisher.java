package com.wuba.crm.tx.outbox.pipeline.stream;

import java.util.List;
import java.util.Map;

/**
 * Redis Stream 写入端口。
 *
 * <p>实现可以是本地 Redis、Cluster 或测试 fake；发送线程只依赖这个接口。</p>
 */
public interface RedisStreamPublisher {

    /**
     * 将一批消息写入指定 Stream。
     *
     * <p>任一条写入失败时应抛出异常，由发送线程决定是否标记 SENT。</p>
     */
    List<String> publish(String stream, List<Map<String, String>> records);
}
