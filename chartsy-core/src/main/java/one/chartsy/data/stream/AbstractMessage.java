/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.stream;

/**
 * An abstract base class for implementing the {@link Message} interface. This class
 * provides a basic implementation of the time-related functionality, storing the
 * timestamp of the message. Subclasses can extend this class to represent specific
 * types of messages.
 */
public abstract class AbstractMessage implements Message {

    private final long time;

    /**
     * Constructs an {@link AbstractMessage} with the specified timestamp.
     *
     * @param time the timestamp of the message
     */
    protected AbstractMessage(long time) {
        this.time = time;
    }

    /**
     * Returns the timestamp of the message.
     *
     * @return the timestamp of the message
     */
    @Override
    public long time() {
        return time;
    }
}