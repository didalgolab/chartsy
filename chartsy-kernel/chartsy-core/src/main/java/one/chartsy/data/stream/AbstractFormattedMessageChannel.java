/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.stream;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/**
 * An abstract base class for message channels responsible for writing formatted text-based messages.
 *
 * <p>This class provides foundational functionality for managing an underlying writer resource,
 * handling common operations such as writing messages, flushing, and disposing resources.</p>
 *
 * <p>Subclasses are expected to implement specific formatting logic by overriding
 * the {@link #formatMessage(Object)} method.</p>
 *
 * @param <T> the type of messages being handled
 */
public abstract class AbstractFormattedMessageChannel<T> implements MessageChannel<T> {

    protected final BufferedWriter writer;
    private final boolean autoFlush;

    /**
     * Constructs a formatted message channel with the specified writer and auto-flush setting.
     *
     * @param writer    the underlying writer resource
     * @param autoFlush whether the writer should automatically flush after each message
     */
    protected AbstractFormattedMessageChannel(Writer writer, boolean autoFlush) {
        Objects.requireNonNull(writer, "writer");
        this.writer = (writer instanceof BufferedWriter buffered) ? buffered : new BufferedWriter(writer);
        this.autoFlush = autoFlush;
    }

    /**
     * Writes a formatted text message to the underlying writer.
     *
     * @param message the formatted message to be written
     * @throws MessageChannelException if an I/O error occurs
     */
    protected void writeMessage(CharSequence message) {
        try {
            writer.append(message);
            writer.newLine();
            if (autoFlush)
                writer.flush();

        } catch (IOException e) {
            throw new MessageChannelException("Failed to write message", e);
        }
    }

    /**
     * Formats the provided message into a text representation.
     *
     * <p>This method must be implemented by subclasses to provide specific formatting logic.</p>
     *
     * @param message the message object to format
     * @return a formatted text representation of the message
     */
    protected abstract CharSequence formatMessage(T message);

    @Override
    public final void send(T message) {
        writeMessage(formatMessage(message));
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new MessageChannelException("Failed to close writer", e);
        }
    }
}
