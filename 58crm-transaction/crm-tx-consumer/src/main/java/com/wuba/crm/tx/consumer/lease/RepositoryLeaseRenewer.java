package com.wuba.crm.tx.consumer.lease;

import com.wuba.crm.tx.consumer.persistence.ConsumerRecordRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class RepositoryLeaseRenewer implements LeaseRenewer {

    private final ConsumerRecordRepository repository;
    private final Duration leaseDuration;
    private final Clock clock;

    public RepositoryLeaseRenewer(
            ConsumerRecordRepository repository,
            Duration leaseDuration) {
        this(repository, leaseDuration, Clock.systemUTC());
    }

    RepositoryLeaseRenewer(
            ConsumerRecordRepository repository,
            Duration leaseDuration,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
    }

    @Override
    public boolean renew(LeaseTask task) {
        Instant now = clock.instant();
        return repository.renewLease(
                task.msgId(),
                task.ownerToken(),
                task.ownerEpoch(),
                now.plus(leaseDuration),
                now) == 1;
    }
}
