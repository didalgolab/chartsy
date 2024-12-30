/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.api.messages;

import one.chartsy.data.stream.Message;
import one.chartsy.data.stream.MessageType;

/**
 * A shutdown request message.
 */
public record ShutdownRequest(long time) implements Message {

    @Override
    public MessageType type() {
        return StandardMessageType.SHUTDOWN_REQUEST;
    }

    /**
     * Converts this {@link ShutdownRequest} into a {@link ShutdownResponse}.
     * The response will have the same timestamp as the request.
     *
     * @param serviceName the identifier of the service being shut down
     * @return a new {@link ShutdownResponse} with the same time as this request
     */
    public ShutdownResponse toShutdownResponse(String serviceName) {
        return new ShutdownResponse(serviceName, time);
    }
}