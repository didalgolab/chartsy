/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.stream;

import one.chartsy.base.Disposable;

/**
 * A generic interface representing a source of messages. The type of message
 * is defined by the generic parameter {@code T}. This interface extends {@link Disposable},
 * allowing the message source to be closed when no longer needed.
 *
 * @param <T> the type of message this source provides
 */
public interface MessageSource<T> extends Disposable {

    /**
     * Retrieves the next message from the source. Implementations should define
     * the behavior for how messages are fetched, such as from a queue, stream,
     * or other data source.
     *
     * @return the next message of type T, or null if no more messages are available
     */
    T getMessage();
}