/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.kernel.data.stream.json;

import one.chartsy.data.stream.AbstractFormattedMessageChannel;
import one.chartsy.core.json.JsonFormatter;
import one.chartsy.data.stream.MessageChannelException;
import one.chartsy.kernel.json.JacksonJsonFormatter;
import org.openide.util.Lookup;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * A message channel that writes messages as JSON Lines (JSONL) format.
 *
 * @param <T> the type of messages being handled
 */
public class JsonlResourceMessageChannel<T> extends AbstractFormattedMessageChannel<T> {

    private final JsonFormatter jsonFormatter;

    private static JsonFormatter defaultJsonFormatter() {
        return Lookup.getDefault().lookup(JacksonJsonFormatter.class);
    }

    /**
     * Constructs a JSONL message channel using default Jackson formatter and auto-flush enabled.
     *
     * @param writer the underlying writer
     */
    public JsonlResourceMessageChannel(Writer writer) {
        this(writer, defaultJsonFormatter(), true);
    }

    /**
     * Constructs a JSONL message channel with custom formatter and auto-flush configuration.
     *
     * @param writer        the underlying writer
     * @param jsonFormatter custom JSON formatter
     * @param autoFlush     whether to automatically flush after each write
     */
    public JsonlResourceMessageChannel(Writer writer, JsonFormatter jsonFormatter, boolean autoFlush) {
        super(writer, autoFlush);
        this.jsonFormatter = jsonFormatter;
    }

    @Override
    protected String formatMessage(T message) {
        return jsonFormatter.toJson(message);
    }

    /**
     * Static factory method to create a JSONL message channel from a given file system Path.
     *
     * @param path the path to the file where messages will be written
     * @param <T>  the type of messages being handled
     * @return a new JsonlResourceMessageChannel instance
     */
    public static <T> JsonlResourceMessageChannel<T> forResource(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return new JsonlResourceMessageChannel<>(writer);
        } catch (IOException e) {
            throw new MessageChannelException("Failed to create writer for path: " + path, e);
        }
    }
}
