package com.wuba.crm.tx.outbox.producer;

/**
 * 单条消息超过 OutboxBuffer 的字节容量。
 *
 * <p>不能无限等待容量：这条消息永远放不进去，Scanner 会永久阻塞。</p>
 */
public class MessageTooLargeException extends RuntimeException {

    public MessageTooLargeException(String msgId, long bytes, long maxBytes) {
        super("message too large, msgId=" + msgId + ", bytes=" + bytes + ", maxBytes=" + maxBytes);
    }
}
