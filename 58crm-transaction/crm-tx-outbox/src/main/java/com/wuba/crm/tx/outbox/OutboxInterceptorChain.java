package com.wuba.crm.tx.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 有序拦截器链。
 *
 * <p>对应 Kafka {@code ProducerInterceptors}：按配置顺序调用 onSend，
 * 前一个结果传给下一个；异常只记录，不阻断发送。</p>
 */
public class OutboxInterceptorChain {

    private static final Logger log = LoggerFactory.getLogger(OutboxInterceptorChain.class);

    private final List<OutboxInterceptor> interceptors;

    public OutboxInterceptorChain(List<OutboxInterceptor> interceptors) {
        this.interceptors = List.copyOf(interceptors);
    }

    public OutboxRecord onSend(OutboxRecord record) {
        // 前一个拦截器返回的 record 传给下一个；异常只记录，不断链。
        OutboxRecord current = record;
        for (OutboxInterceptor interceptor : interceptors) {
            try {
                OutboxRecord next = interceptor.onSend(current);
                if (next != null) {
                    current = next;
                }
            } catch (RuntimeException ex) {
                log.warn("outbox interceptor onSend failed, interceptor={}",
                        interceptor.getClass().getName(), ex);
            }
        }
        return current;
    }

    public void onAcknowledgement(OutboxSendResult result, Throwable error) {
        // 发送线程里执行，拦截器实现必须快；异常不能影响发送结果。
        for (OutboxInterceptor interceptor : interceptors) {
            try {
                interceptor.onAcknowledgement(result, error);
            } catch (RuntimeException ex) {
                log.warn("outbox interceptor acknowledgement failed, interceptor={}",
                        interceptor.getClass().getName(), ex);
            }
        }
    }

    public void close() {
        for (OutboxInterceptor interceptor : interceptors) {
            try {
                interceptor.close();
            } catch (RuntimeException ex) {
                log.warn("outbox interceptor close failed, interceptor={}",
                        interceptor.getClass().getName(), ex);
            }
        }
    }
}
