package com.wuba.crm.tx.consumer.lease;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InFlightRegistry {

    private final ConcurrentMap<String, LeaseTask> tasks = new ConcurrentHashMap<>();

    public void register(LeaseTask task) {
        Objects.requireNonNull(task, "task");
        LeaseTask existing = tasks.putIfAbsent(task.key(), task);
        if (existing != null) {
            throw new IllegalStateException("task already in flight: " + task.key());
        }
    }

    public void unregister(LeaseTask task) {
        if (task != null) {
            tasks.remove(task.key(), task);
        }
    }

    public boolean contains(LeaseTask task) {
        return tasks.get(task.key()) == task;
    }

    public Collection<LeaseTask> snapshot() {
        return List.copyOf(tasks.values());
    }

    public int size() {
        return tasks.size();
    }
}
