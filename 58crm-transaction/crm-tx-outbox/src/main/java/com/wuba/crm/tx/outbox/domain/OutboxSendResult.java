package com.wuba.crm.tx.outbox.domain;

/**
 * 一次 Stream 写入的结果元数据。
 *
 * @param stream 业务线对应的 Stream key
 * @param msgId  消息正本 ID
 * @param entryId Redis Stream entry ID；发送失败时为 null
 */
public record OutboxSendResult(String stream, String msgId, String entryId) {
}
