package com.wuba.crm.tx.consumer.persist;

import com.wuba.crm.tx.consumer.domain.ConsumedMessage;
import com.wuba.crm.tx.consumer.persistence.ConsumerRecordRepository;
import com.wuba.crm.tx.consumer.pull.BoundedStreamQueue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumePersisterTest {

    @Test
    void persistOnce_persistsBatchAsReady() throws Exception {
        ConsumerRecordRepository repository = mock(ConsumerRecordRepository.class);
        when(repository.insertReadyBatch(anyList(), any(Instant.class))).thenReturn(2);
        BoundedStreamQueue queue = new BoundedStreamQueue(10);
        queue.putAll(List.of(message("m-1"), message("m-2")));
        ConsumePersister persister = new ConsumePersister(
                queue, repository, 500, Duration.ofMillis(10));

        assertEquals(2, persister.persistOnce());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ConsumedMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).insertReadyBatch(captor.capture(), any(Instant.class));
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void persistOnce_emptyBatch_doesNothing() throws Exception {
        ConsumerRecordRepository repository = mock(ConsumerRecordRepository.class);
        ConsumePersister persister = new ConsumePersister(
                new BoundedStreamQueue(10), repository, 500, Duration.ofMillis(10));

        assertEquals(0, persister.persistOnce());
        verify(repository, org.mockito.Mockito.never())
                .insertReadyBatch(anyList(), any(Instant.class));
    }

    @Test
    void close_stopsRunningPersister() {
        ConsumerRecordRepository repository = mock(ConsumerRecordRepository.class);
        ConsumePersister persister = new ConsumePersister(
                new BoundedStreamQueue(10), repository, 500, Duration.ofMillis(10));

        persister.start();
        persister.close();

        assertTrue(!persister.isRunning());
    }

    private ConsumedMessage message(String msgId) {
        return new ConsumedMessage(
                msgId, "lead", "lead-1", "LEAD_ASSIGNED", "1-0", "{}", null);
    }
}
