package com.wuba.crm.tx.outbox.producer;

import com.wuba.crm.tx.outbox.domain.LocalMessage;
import com.wuba.crm.tx.outbox.domain.OutboxRecord;
import com.wuba.crm.tx.outbox.persistence.LocalMessageRepository;

import java.util.List;

/**
 * 扫描 MySQL Outbox，把待发送消息放入有界内存 buffer。
 *
 * <p>扫描线程不发送消息，也不修改状态。它只负责把数据库中的待投递候选转成
 * {@link OutboxRecord}，并通过 {@link OutboxInFlight} 去重。</p>
 */
public class OutboxScanner {

    private final LocalMessageRepository repository;
    private final OutboxBuffer buffer;
    // 与 Sender 共享：消息一旦入队/发送中，下一轮扫描不能重复放入。
    private final OutboxInFlight inFlight;
    private final int scanBatch;

    public OutboxScanner(
            LocalMessageRepository repository,
            OutboxBuffer buffer,
            OutboxInFlight inFlight,
            int scanBatch) {
        this.repository = repository;
        this.buffer = buffer;
        this.inFlight = inFlight;
        this.scanBatch = scanBatch;
    }

    /**
     * 执行一轮扫描。
     *
     * @return 本轮放入 buffer 的消息数
     */
    public int scanOnce() throws InterruptedException {
        // 只查 INIT；SENT 是生产方终态，不能再靠超时重发。
        List<LocalMessage> candidates = repository.findRelayCandidates(scanBatch);
        int accepted = 0;
        for (LocalMessage candidate : candidates) {
            // add 返回 false 说明消息已经在内存队列/发送中，直接跳过。
            if (!inFlight.add(candidate.msgId())) {
                continue;
            }
            try {
                // buffer 满时这里会阻塞，Scanner 被反压，不会无限堆内存。
                buffer.put(OutboxRecord.from(candidate));
                accepted++;
            } catch (InterruptedException ex) {
                // 线程被关闭时释放当前消息，让下一次进程启动后还能重新扫描。
                inFlight.remove(candidate.msgId());
                throw ex;
            }
        }
        return accepted;
    }
}
