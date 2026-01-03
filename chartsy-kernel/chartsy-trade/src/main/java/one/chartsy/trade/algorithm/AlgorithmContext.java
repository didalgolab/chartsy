/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.data.stream.Message;
import one.chartsy.data.stream.MessageChannel;
import one.chartsy.data.stream.MessageChannelException;
import one.chartsy.messaging.common.handlers.ShutdownResponseHandler;
import one.chartsy.time.Clock;
import one.chartsy.trade.service.OrderHandler;
import one.chartsy.util.SequenceGenerator;
import org.springframework.beans.PropertyAccessor;

/**
 * Provides the context necessary to configure and run an {@link Algorithm}.
 * The context typically includes configuration parameters, identifiers,
 * and other relevant data that the algorithm needs to operate.
 */
public interface AlgorithmContext {

    /**
     * Provides the unique identifier or descriptive name associated with the algorithm.
     *
     * @return the name identifying the algorithm
     */
    String getId();

    /**
     * Provides the clock instance driving time-related operations, events, and simulations
     * within the algorithm execution environment.
     *
     * @return the {@link Clock} instance for this context
     */
    Clock getClock();

    /**
     * Obtains the order event handler responsible for processing order-related events
     * within the algorithm context.
     *
     * @return the {@link OrderHandler} for handling order events
     */
    OrderHandler getOrderHandler();

    /**
     * Provides a sequence generator that can be used to generate unique identifiers or
     * sequence numbers for various algorithm operations, such as order IDs or event sequences.
     *
     * @return the {@link SequenceGenerator} for generating unique sequences
     */
    SequenceGenerator getSequenceGenerator();

    /**
     * Gives the primary message channel for sending or receiving generic algorithm messages.
     *
     * @return the primary {@link MessageChannel} for general-purpose messaging
     */
    MessageChannel<Message> getMessageChannel();

    /**
     * Adds a shutdown response handler that will be invoked when the algorithm or context
     * is requested to shut down.
     *
     * @param handler the shutdown response handler to register
     */
    void addShutdownResponseHandler(ShutdownResponseHandler handler);

    /**
     * Removes a previously registered shutdown response handler.
     *
     * @param handler the shutdown response handler to unregister
     */
    void removeShutdownResponseHandler(ShutdownResponseHandler handler);

    /**
     * Obtains the currently active shutdown response handler.
     *
     * @return the active {@link ShutdownResponseHandler}
     */
    ShutdownResponseHandler getShutdownResponseHandler();

    /**
     * Indicates whether the algorithm or context has initiated or completed a shutdown sequence.
     *
     * @return {@code true} if shutdown has been initiated or completed; {@code false} otherwise
     */
    boolean isShutdown();

    /**
     * Creates a new output channel for writing messages, supporting basic placeholder resolution.
     *
     * <p>This convenience method delegates to {@link #createOutputChannel(String, Class, PropertyAccessor)},
     * providing {@code null} for the property accessor, thus supporting only built-in placeholders
     * like timestamps and UUIDs.</p>
     *
     * @param channelId   identifier of the channel or file path potentially containing placeholders
     * @param messageType the type of messages to write to the channel
     * @param <T>         the type parameter of messages
     * @return a new, configured {@link MessageChannel} for output
     * @throws IllegalArgumentException if the provided file extension or identifier is unsupported
     * @throws MessageChannelException  if an error occurs while setting up the channel
     */
    default <T> MessageChannel<T> createOutputChannel(String channelId, Class<T> messageType) {
        return createOutputChannel(channelId, messageType, null);
    }

    /**
     * Creates a new output channel capable of writing messages to the specified channel, resolving placeholders
     * dynamically via the provided {@link PropertyAccessor}. The parent directories for the output file will be
     * created if they do not exist.
     * <p>
     * Supported placeholders include:
     * <ul>
     *   <li><b>${now?pattern}</b> – inserts a timestamp formatted with the specified pattern.</li>
     *   <li><b>${uuid}</b> – inserts a random UUID.</li>
     *   <li><b>${property.name}</b> – resolves using the provided {@code PropertyAccessor}.</li>
     * </ul>
     *
     * <p>The method supports the following file formats based on the specified channel ID extension:</p>
     * <ul>
     *   <li><b>.jsonl</b>: JSON Lines format</li>
     *   <li><b>.csv</b>: Comma-Separated Values format</li>
     * </ul>
     *
     * <p>Optional file compression is supported by appending:</p>
     * <ul>
     *   <li><b>.gz</b>: GZIP compression</li>
     *   <li><b>.zip</b>: ZIP compression</li>
     * </ul>
     *
     * @param channelId        the channel identifier or file path potentially containing placeholders
     * @param messageType      the type of messages that will be written to the channel
     * @param propertyAccessor accessor for resolving custom property placeholders; may be {@code null}
     * @param <T> the message type parameter
     * @return a newly instantiated and fully configured {@link MessageChannel} for output
     * @throws IllegalArgumentException if the file extension is unsupported
     * @throws MessageChannelException  if an I/O error occurs during creation
     */
    <T> MessageChannel<T> createOutputChannel(String channelId, Class<T> messageType, PropertyAccessor propertyAccessor);

}