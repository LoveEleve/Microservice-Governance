package com.wuba.crm.tx.outbox.producer;

import com.wuba.crm.tx.outbox.config.BizCodeRegistry;
import com.wuba.crm.tx.outbox.config.UnknownBizCodeException;
import com.wuba.crm.tx.outbox.domain.LocalMessage;
import com.wuba.crm.tx.outbox.domain.OutboxRecord;
import com.wuba.crm.tx.outbox.persistence.LocalMessageRepository;

import java.util.ArrayList;
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
    private final BizCodeRegistry registry;
    // Scanner 轮询起点；每轮从下一个 bizCode 开始，避免固定头部饥饿。
    private int cursor;

    public OutboxScanner(
            LocalMessageRepository repository,
            OutboxBuffer buffer,
            OutboxInFlight inFlight,
            int scanBatch,
            BizCodeRegistry registry) {
        this.repository = repository;
        this.buffer = buffer;
        this.inFlight = inFlight;
        this.scanBatch = scanBatch;
        this.registry = registry;
        this.cursor = 0;
    }

    /**
     * 执行一轮扫描。
     *
     * @return 本轮放入 buffer 的消息数
     */
    public int scanOnce() throws InterruptedException {
        List<String> codes = new ArrayList<>(registry.bizCodes());
        codes.sort(String::compareTo);
        if (codes.isEmpty()) {
            return 0;
        }

        // 每个业务线每轮至少一个名额，避免 lead 占满全局 LIMIT。
        int perBizLimit = Math.max(1, (scanBatch + codes.size() - 1) / codes.size());
        int accepted = 0;
        for (int i = 0; i < codes.size(); i++) {
            String bizCode = codes.get((cursor + i) % codes.size());
            List<LocalMessage> candidates =
                    repository.findRelayCandidatesByBizCode(bizCode, perBizLimit);
            for (LocalMessage candidate : candidates) {
                if (!inFlight.add(candidate.msgId())) {
                    continue;
                }
                try {
                    registry.require(candidate.bizCode());
                    // 某个业务线 buffer 满了不能阻塞其它业务线：跳过，下一轮再扫。
                    if (!buffer.tryPut(OutboxRecord.from(candidate))) {
                        inFlight.remove(candidate.msgId());
                        continue;
                    }
                    accepted++;
                } catch (UnknownBizCodeException ex) {
                    reject(candidate);
                } catch (MessageTooLargeException ex) {
                    reject(candidate);
                } catch (InterruptedException ex) {
                    inFlight.remove(candidate.msgId());
                    throw ex;
                }
            }
        }
        cursor = (cursor + 1) % codes.size();
        return accepted;
    }

    private void reject(LocalMessage candidate) {
        // maxRetries=1 表示不再自动重试，直接 DEAD，交给人工处理。
        inFlight.remove(candidate.msgId());
        repository.markFailedBatch(
                java.util.List.of(candidate), java.time.Instant.now(), 1);
    }
}
