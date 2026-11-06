package com.wuba.crm.tx.outbox.api;

/**
 * 业务方发布可靠事件的唯一入口。
 *
 * <p>实现必须在调用方已有的本地事务中执行，不允许自己开启新事务，也不允许
 * 在事务提交后异步补写消息。否则会出现“业务成功、消息丢失”或
 * “消息存在、业务回滚”的不一致。</p>
 */
public interface OutboxPublisher {

    /**
     * 在当前本地事务中写入一条 INIT 消息。
     *
     * @param event 业务事件
     * @return 生成的本地消息 ID
     */
    String publish(BusinessEvent event);
}
