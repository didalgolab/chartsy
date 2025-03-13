/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.api.messages.handlers.ShutdownResponseHandler;
import one.chartsy.data.stream.Message;
import one.chartsy.data.stream.MessageChannel;
import one.chartsy.time.Clock;

import java.nio.file.Path;

/**
 * Provides the context necessary to configure and run an {@link Algorithm}.
 * The context typically includes configuration parameters, identifiers,
 * and other relevant data that the algorithm needs to operate.
 */
public interface AlgorithmContext {

    /**
     * Gives the unique name associated with this algorithm context. This could be
     * the name of the strategy or a descriptive identifier.
     *
     * @return a {@code String} representing the name of the current algorithm
     */
    String getName();

    Clock getClock();

    MessageChannel<Message> getMessageChannel();

    void addShutdownResponseHandler(ShutdownResponseHandler handler);

    void removeShutdownResponseHandler(ShutdownResponseHandler handler);

    ShutdownResponseHandler getShutdownResponseHandler();

    boolean isShutdown();

    /**
     * Creates a message channel for writing messages to the file specified by the given path.
     * Currently, the only supported file formats (extensions) are:
     * <ul>
     * <li><b>".jsonl":</b> JSON Lines format</li>
     * <li><b>".csv":</b> CSV format</li>
     * </ul>
     *
     * @param path the file path where messages will be written
     * @param messageType the type of messages that will be written to the channel
     * @return a new {@code MessageChannel} for output
     * @throws IllegalArgumentException if the file extension is unsupported
     */
    <T> MessageChannel<T> createOutputChannel(Path path, Class<T> messageType);

}