package com.wuba.crm.tx.outbox;

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
        List<LocalMessage> candidates = repository.findRelayCandidates(scanBatch);
        int accepted = 0;
        for (LocalMessage candidate : candidates) {
            if (!inFlight.add(candidate.msgId())) {
                continue;
            }
            try {
                buffer.put(OutboxRecord.from(candidate));
                accepted++;
            } catch (InterruptedException ex) {
                inFlight.remove(candidate.msgId());
                throw ex;
            }
        }
        return accepted;
    }
}
