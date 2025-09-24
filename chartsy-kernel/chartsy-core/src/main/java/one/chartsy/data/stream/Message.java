/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.stream;

import one.chartsy.time.Chronological;

/**
 * Represents a generic message in the system. Every message has a timestamp
 * representing the time the message was created or received.
 */
public interface Message extends Chronological {

    default String sourceId() {
        return null;
    }

    default String destinationId() {
        return null;
    }
}