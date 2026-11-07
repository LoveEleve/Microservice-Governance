package com.wuba.crm.tx.consumer.lease;

import com.wuba.crm.tx.consumer.persistence.ConsumerRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryLeaseRenewerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void renew_setsLeaseUntilFromClock() {
        ConsumerRecordRepository repository = mock(ConsumerRecordRepository.class);
        when(repository.renewLease(
                anyString(), anyString(), anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        RepositoryLeaseRenewer renewer = new RepositoryLeaseRenewer(
                repository, Duration.ofSeconds(60), Clock.fixed(NOW, ZoneOffset.UTC));
        LeaseTask task = new LeaseTask(
                "m-1", "lead", "consumer-1", "owner-1", 1, NOW.plusSeconds(300));

        assertTrue(renewer.renew(task));
        verify(repository).renewLease(
                "m-1", "owner-1", 1, NOW.plusSeconds(60), NOW);
    }

    @Test
    void renew_returnsFalseWhenRepositoryRejects() {
        ConsumerRecordRepository repository = mock(ConsumerRecordRepository.class);
        when(repository.renewLease(
                anyString(), anyString(), anyLong(), any(Instant.class), any(Instant.class)))
                .thenReturn(0);
        RepositoryLeaseRenewer renewer = new RepositoryLeaseRenewer(
                repository, Duration.ofSeconds(60), Clock.fixed(NOW, ZoneOffset.UTC));

        assertFalse(renewer.renew(new LeaseTask(
                "m-1", "lead", "consumer-1", "owner-1", 1, NOW.plusSeconds(300))));
    }
}
