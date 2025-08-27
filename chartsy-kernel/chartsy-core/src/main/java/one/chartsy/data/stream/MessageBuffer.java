/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.stream;

/**
 * Represents a buffer that holds messages and provides a mechanism to read
 * and process these messages using a {@link MessageHandler}.
 */
public interface MessageBuffer {

    /**
     * Reads and processes messages from the buffer, invoking the provided
     * {@link MessageHandler} for each message. The number of messages processed
     * is limited by the {@code pollLimit} parameter.
     *
     * @param handler      the {@link MessageHandler} to process each message
     * @param pollLimit the maximum number of messages to process in this call
     * @return the actual number of messages that were processed
     */
    int read(MessageHandler handler, int pollLimit);

    /**
     * Reads and processes messages from the buffer, invoking the provided
     * {@link MessageHandler} for each message. This method processes as many
     * messages as possible, up to {@link Integer#MAX_VALUE}.
     *
     * @param handler the {@link MessageHandler} to process each message
     * @return the actual number of messages that were processed
     */
    default int read(MessageHandler handler) {
        return read(handler, Integer.MAX_VALUE);
    }
}
