package com.wuba.crm.tx.consumer.ack;

import java.util.Objects;

public record AckCommand(String stream, String group, String entryId) {

    public AckCommand {
        requireText(stream, "stream");
        requireText(group, "group");
        requireText(entryId, "entryId");
        Objects.requireNonNull(entryId, "entryId");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
