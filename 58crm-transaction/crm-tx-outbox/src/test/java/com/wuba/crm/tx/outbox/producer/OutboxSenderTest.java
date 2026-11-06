package com.wuba.crm.tx.outbox.producer;

import com.wuba.crm.tx.outbox.api.*;
import com.wuba.crm.tx.outbox.config.*;
import com.wuba.crm.tx.outbox.domain.*;
import com.wuba.crm.tx.outbox.persistence.*;
import com.wuba.crm.tx.outbox.producer.*;
import com.wuba.crm.tx.outbox.pipeline.interceptor.*;
import com.wuba.crm.tx.outbox.pipeline.route.*;
import com.wuba.crm.tx.outbox.pipeline.serializer.*;
import com.wuba.crm.tx.outbox.pipeline.stream.*;


import com.wuba.crm.tx.common.MessageStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发送线程只从 buffer 取消息，按 Stream 分组写入，不直接扫库。
 */
class OutboxSenderTest {

    @Test
    void sendOnce_publishesBatchAndMarksSent() throws Exception {
        OutboxBuffer buffer = new OutboxBuffer(10, 4096);
        buffer.put(record("m-1", "lead"));
        FakePublisher publisher = new FakePublisher();
        FakeRepository repository = new FakeRepository();
        OutboxSender sender = sender(buffer, publisher, repository, new OutboxInFlight());

        int sent = sender.sendOnce();

        assertEquals(1, sent);
        assertEquals(1, publisher.calls);
        assertEquals("tx:stream:lead", publisher.lastStream);
        assertEquals(1, publisher.lastRecords.size());
        assertEquals("m-1", publisher.lastRecords.get(0).get("msgId"));
        assertEquals(1, repository.marked);
    }

    @Test
    void sendOnce_doesNotMarkSentWhenStreamFails() throws Exception {
        OutboxBuffer buffer = new OutboxBuffer(10, 4096);
        buffer.put(record("m-1", "lead"));
        FakePublisher publisher = new FakePublisher();
        publisher.fail = true;
        FakeRepository repository = new FakeRepository();
        OutboxInFlight inFlight = new OutboxInFlight();
        inFlight.add("m-1");
        OutboxSender sender = sender(buffer, publisher, repository, inFlight);

        int sent = sender.sendOnce();

        assertEquals(0, sent);
        assertEquals(0, repository.marked);
        assertEquals(1, repository.failed);
        assertEquals(0, inFlight.size());
    }

    @Test
    void sendOnce_marksFailedWhenSerializerThrows() throws Exception {
        OutboxBuffer buffer = new OutboxBuffer(10, 4096);
        buffer.put(record("m-1", "lead"));
        FakePublisher publisher = new FakePublisher();
        FakeRepository repository = new FakeRepository();
        OutboxInFlight inFlight = new OutboxInFlight();
        inFlight.add("m-1");
        OutboxSender sender = sender(
                buffer,
                publisher,
                repository,
                inFlight,
                record -> {
                    throw new IllegalStateException("serialize failed");
                });

        int sent = sender.sendOnce();

        assertEquals(0, sent);
        assertEquals(0, repository.marked);
        assertEquals(1, repository.failed);
        assertEquals(0, inFlight.size());
    }

    @Test
    void sendOnce_groupsByBusinessLine() throws Exception {
        OutboxBuffer buffer = new OutboxBuffer(10, 4096);
        buffer.put(record("m-1", "lead"));
        buffer.put(record("m-2", "quota"));
        FakePublisher publisher = new FakePublisher();
        FakeRepository repository = new FakeRepository();
        OutboxSender sender = sender(buffer, publisher, repository, new OutboxInFlight());

        sender.sendOnce();
        sender.sendOnce();

        assertEquals(2, publisher.calls);
        assertTrue(publisher.streams.contains("tx:stream:lead"));
        assertTrue(publisher.streams.contains("tx:stream:quota"));
    }

    private static OutboxSender sender(
            OutboxBuffer buffer,
            FakePublisher publisher,
            FakeRepository repository,
            OutboxInFlight inFlight) {
        return sender(buffer, publisher, repository, inFlight, new DefaultOutboxSerializer());
    }

    private static OutboxSender sender(
            OutboxBuffer buffer,
            FakePublisher publisher,
            FakeRepository repository,
            OutboxInFlight inFlight,
            OutboxSerializer serializer) {
        OutboxConfig config = new OutboxConfig(
                "tx:stream:",
                Duration.ofMillis(1),
                10,
                100,
                4096,
                100,
                Duration.ofMillis(10),
                Duration.ofSeconds(30),
                5);
        return new OutboxSender(
                buffer,
                publisher,
                new OutboxInterceptorChain(List.of()),
                new BizCodePartitioner(),
                serializer,
                repository,
                config,
                inFlight);
    }

    private static OutboxRecord record(String msgId, String bizCode) {
        return new OutboxRecord(
                msgId,
                "event-" + msgId,
                bizCode,
                "biz-" + msgId,
                "LEAD_ASSIGNED",
                "{}",
                null,
                MessageStatus.INIT,
                0);
    }

    private static final class FakeRepository implements LocalMessageRepository {

        private int marked;
        private int failed;

        @Override
        public List<LocalMessage> findRelayCandidates(int limit) {
            return List.of();
        }

        @Override
        public int markSentBatch(List<LocalMessage> messages, java.time.Instant nextRetryAt) {
            marked += messages.size();
            return messages.size();
        }

        @Override
        public int markFailedBatch(List<LocalMessage> messages, java.time.Instant nextRetryAt, int maxRetries) {
            failed += messages.size();
            return messages.size();
        }
    }

    private static final class FakePublisher implements RedisStreamPublisher {

        private int calls;
        private boolean fail;
        private String lastStream;
        private List<Map<String, String>> lastRecords = List.of();
        private final java.util.Set<String> streams = new java.util.HashSet<>();

        @Override
        public void publish(String stream, List<Map<String, String>> records) {
            calls++;
            lastStream = stream;
            lastRecords = records;
            streams.add(stream);
            if (fail) {
                throw new IllegalStateException("redis stream unavailable");
            }
        }
    }
}
