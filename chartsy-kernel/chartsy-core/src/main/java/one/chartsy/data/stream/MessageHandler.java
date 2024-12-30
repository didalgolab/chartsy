/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.stream;

/**
 * A handler for processing messages. Implementations of this interface define
 * how to handle a specific type of {@link Message}.
 */
public interface MessageHandler {

    /**
     * Handles the given message. The implementation should define what actions
     * to take upon receiving the message, such as logging, processing, or reacting
     * to the data contained within the message.
     *
     * @param message the message to be handled
     */
    void handleMessage(Message message);
}