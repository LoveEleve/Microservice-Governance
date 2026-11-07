package com.wuba.crm.tx.consumer.pull;

import com.wuba.crm.tx.consumer.domain.ConsumedMessage;

import java.time.Duration;
import java.util.List;

public interface StreamSource {

    List<ConsumedMessage> read(
            String stream,
            String group,
            String consumer,
            int count,
            Duration block);
}
