package com.wuba.crm.tx.tcc.client;

import java.time.Duration;

/**
 * 注解事务的 begin 参数。
 *
 * @param requestId       业务幂等键
 * @param bizCode         业务域
 * @param bizId           业务单号
 * @param tryTimeout      Try 超时
 */
public record TccTransactionOptions(
        String requestId,
        String bizCode,
        String bizId,
        Duration tryTimeout) {
}
