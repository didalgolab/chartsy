/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.messaging;

import one.chartsy.data.stream.MessageSource;

/**
 * A specialized interface representing a source of {@link MarketMessage} instances.
 * This interface extends {@link MessageSource} specifically for market messages.
 */
public interface MarketMessageSource extends MessageSource<MarketMessage> {

    /**
     * Retrieves the next {@link MarketMessage} from the source.
     *
     * @return the next {@link MarketMessage}, or {@code null} if no more messages are available
     */
    @Override
    MarketMessage getMessage();

    /**
     * Checks if the message source is open and available for providing messages.
     * When open, messages can be retrieved from this source.
     *
     * @return {@code true} if the source is open and available, {@code false} otherwise
     */
    boolean isOpen();
}
