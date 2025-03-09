/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.stream;

import one.chartsy.base.Disposable;

/**
 * A message sink that consumes messages.
 */
public interface MessageChannel<T> extends Disposable {

    /**
     * This method is invoked to send a message to the channel.
     * 
     * @param message the payload
     */
    void send(T message);

    /**
     * Sends multiple messages through the channel.
     * <p>
     * Default implementation sends each message individually but subclasses are encouraged
     * to provide more performant implementations if possible.
     *
     * @param messages iterable of messages to send
     */
    default void sendAll(Iterable<T> messages) {
        messages.forEach(this::send);
    }
}