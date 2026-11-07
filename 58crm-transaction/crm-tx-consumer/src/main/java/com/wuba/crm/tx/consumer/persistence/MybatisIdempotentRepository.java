package com.wuba.crm.tx.consumer.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public class MybatisIdempotentRepository implements IdempotentRepository {

    private final IdempotentMapper mapper;

    public MybatisIdempotentRepository(IdempotentMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public boolean tryAcquire(String msgId, String bizKey, Instant now) {
        return mapper.insertIgnore(
                msgId,
                bizKey,
                "DONE",
                LocalDateTime.ofInstant(now, ZoneOffset.UTC)) == 1;
    }
}
