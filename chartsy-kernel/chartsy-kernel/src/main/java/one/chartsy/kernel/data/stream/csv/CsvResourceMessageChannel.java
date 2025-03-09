/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.kernel.data.stream.csv;

import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import one.chartsy.data.stream.MessageChannel;
import one.chartsy.data.stream.MessageChannelException;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * A message channel that writes messages in CSV format.
 *
 * @param <T> the type of messages being handled
 */
public class CsvResourceMessageChannel<T> implements MessageChannel<T> {

    private final CsvMapper mapper = new CsvMapper();
    private final SequenceWriter writer;
    private final boolean autoFlush;

    /**
     * Constructs a CSV message channel with the given writer, CSV formatter, and auto-flush setting.
     *
     * @param out the underlying writer
     * @param type the class representing the POJO type to convert to CSV
     * @param autoFlush whether to automatically flush after each write
     */
    public CsvResourceMessageChannel(Writer out, Class<T> type, boolean autoFlush) throws IOException {
        this.autoFlush = autoFlush;
        this.mapper.registerModule(new JavaTimeModule());
        this.writer = mapper.writer(createSchema(type)).writeValues(out);
    }

    protected CsvSchema createSchema(Class<?> type) {
        return mapper.schemaFor(type)
                .withHeader()
                .withColumnSeparator(';')
                .withColumnReordering(false);
    }

    /**
     * Overrides send() to use the SequenceWriter so that the header is not repeated.
     *
     * @param message the message to write
     */
    @Override
    public void send(T message) {
        try {
            writer.write(message);
            if (autoFlush)
                writer.flush();

        } catch (IOException e) {
            throw new MessageChannelException("Error writing message " + message, e);
        }
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new MessageChannelException("Error closing writer", e);
        }
    }

    /**
     * Factory method to create a CSV message channel from a given Path.
     *
     * @param path the file system path for the CSV file
     * @param <T> the type of messages being handled
     * @return a new CsvResourceMessageChannel instance
     */
    public static <T> CsvResourceMessageChannel<T> forResource(Path path, Class<T> type) {
        Objects.requireNonNull(path, "path cannot be null");
        try {
            Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return new CsvResourceMessageChannel<>(writer, type, true);
        } catch (IOException e) {
            throw new MessageChannelException("Failed to create writer for path: " + path, e);
        }
    }
}
