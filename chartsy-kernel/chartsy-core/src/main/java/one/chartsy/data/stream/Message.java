/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.stream;

/**
 * Represents a generic message in the system. Every message has a timestamp
 * representing the time the message was created or received.
 */
public interface Message {

    /**
     * Returns the timestamp of the message, typically representing the time the
     * message was created or received. The time is expressed as a long value,
     * in nanoseconds since the epoch.
     *
     * @return the timestamp of the message
     */
    long time();

    default String sourceId() {
        return null;
    }

    default String destinationId() {
        return null;
    }
}