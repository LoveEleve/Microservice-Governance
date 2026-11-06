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
