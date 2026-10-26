/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loveeleve.polycache.redisson.capture;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Outbound {@link io.netty.channel.ChannelHandler} that observes every command written by the
 * Redisson connection and delegates the write commands to the {@link RedisCommandCapture}.
 *
 * <p>The handler is added to the tail of the pipeline, so it observes the {@link CommandData}
 * before the Redisson {@code CommandEncoder} encodes it.
 *
 * @author <a href="https://github.com/LoveEleve">LoveEleve</a>
 * @since 1.0.0
 */
class CommandCaptureHandler extends ChannelOutboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CommandCaptureHandler.class);

    static final String NAME = "polycache-command-capture";

    private final RedisCommandCapture capture;

    CommandCaptureHandler(RedisCommandCapture capture) {
        this.capture = capture;
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        captureSafely(message, promise);
        super.write(context, message, promise);
    }

    private void captureSafely(Object message, ChannelPromise promise) {
        try {
            if (message instanceof CommandData) {
                capture.capture((CommandData<?, ?>) message);
            } else if (message instanceof CommandsData) {
                CommandsData commandsData = (CommandsData) message;
                if (commandsData.isSkipResult()) {
                    // A skipResult batch does not complete the promises of its inner commands, and
                    // the batch promise is not a reliable completion signal for every Redisson
                    // version. The channel write promise is the only signal available here.
                    capture.capture(commandsData, promise);
                } else {
                    capture.capture(commandsData);
                }
            }
        } catch (Throwable e) {
            // The capture must never affect the Redis command itself
            logger.warn("Failed to capture the Redis command, the command execution is not affected", e);
        }
    }
}
