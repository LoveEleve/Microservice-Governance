package com.wuba.crm.tx.consumer.lease;

import com.wuba.crm.tx.consumer.persistence.ConsumerRecordRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    @Override
    public Set<String> renewBatch(List<LeaseTask> tasks) {
        Set<String> renewed = new HashSet<>();
        Map<Owner, List<LeaseTask>> groups = new LinkedHashMap<>();
        for (LeaseTask task : tasks) {
            groups.computeIfAbsent(new Owner(task.ownerToken(), task.ownerEpoch()),
                            ignored -> new ArrayList<>())
                    .add(task);
        }
        Instant now = clock.instant();
        Instant leaseUntil = now.plus(leaseDuration);
        for (Map.Entry<Owner, List<LeaseTask>> entry : groups.entrySet()) {
            List<LeaseTask> group = entry.getValue();
            List<String> msgIds = group.stream().map(LeaseTask::msgId).toList();
            int affected = repository.renewLeaseBatch(
                    msgIds,
                    entry.getKey().token(),
                    entry.getKey().epoch(),
                    leaseUntil,
                    now);
            if (affected == group.size()) {
                group.forEach(task -> renewed.add(task.key()));
                continue;
            }
            for (LeaseTask task : group) {
                if (renew(task)) {
                    renewed.add(task.key());
                }
            }
        }
        return renewed;
    }

    private record Owner(String token, long epoch) {
    }
}
