package com.wuba.crm.tx.consumer.ack;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AckBatcherTest {

    @Test
    void flush_groupsByStreamAndAcknowledges() throws Exception {
        RecordingAcknowledger acknowledger = new RecordingAcknowledger();
        AckBatcher batcher = new AckBatcher(
                acknowledger, 10, 100, Duration.ofMillis(10));
        batcher.add(new AckCommand("stream-a", "group-a", "1-0"));
        batcher.add(new AckCommand("stream-a", "group-a", "2-0"));
        batcher.add(new AckCommand("stream-b", "group-b", "3-0"));

        assertEquals(3, batcher.flushOnce());
        assertEquals(2, acknowledger.calls.size());
        assertEquals(List.of("1-0", "2-0"), acknowledger.byKey("stream-a/group-a"));
        assertEquals(List.of("3-0"), acknowledger.byKey("stream-b/group-b"));
    }

    @Test
    void flush_empty_returnsZero() throws Exception {
        AckBatcher batcher = new AckBatcher(
                new RecordingAcknowledger(), 10, 100, Duration.ofMillis(10));

        assertEquals(0, batcher.flushOnce());
    }

    private static final class RecordingAcknowledger implements StreamAcknowledger {

        private final List<Call> calls = new ArrayList<>();

        @Override
        public long acknowledge(String stream, String group, String... entryIds) {
            calls.add(new Call(stream, group, List.of(entryIds)));
            return entryIds.length;
        }

        List<String> byKey(String key) {
            return calls.stream()
                    .filter(call -> key.equals(call.stream() + "/" + call.group()))
                    .flatMap(call -> call.entryIds().stream())
                    .toList();
        }
    }

    private record Call(String stream, String group, List<String> entryIds) {
    }
}
