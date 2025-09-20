/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.core.event.ListenerList;
import one.chartsy.data.stream.MessageChannel;
import one.chartsy.data.stream.MessageChannelException;
import one.chartsy.kernel.data.stream.csv.CsvResourceMessageChannel;
import one.chartsy.kernel.data.stream.json.JsonlResourceMessageChannel;
import one.chartsy.messaging.common.handlers.ShutdownResponseHandler;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.PropertyAccessor;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public abstract class AbstractAlgorithmContext implements AlgorithmContext {

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\$\\{(now\\?([^}]+)|uuid|[^}]+)}");

    private final ListenerList<ShutdownResponseHandler> shutdownResponseHandlers = ListenerList.of(ShutdownResponseHandler.class);
    private final String id;
    private volatile boolean shutdown;


    protected AbstractAlgorithmContext(String id) {
        this.id = id;
        addShutdownResponseHandler(__ -> this.shutdown = true);
    }

    @Override
    public final String getId() {
        return id;
    }

    @Override
    public void addShutdownResponseHandler(ShutdownResponseHandler handler) {
        shutdownResponseHandlers.addListener(handler);
    }

    @Override
    public void removeShutdownResponseHandler(ShutdownResponseHandler handler) {
        shutdownResponseHandlers.removeListener(handler);
    }

    @Override
    public final ShutdownResponseHandler getShutdownResponseHandler() {
        return shutdownResponseHandlers.fire();
    }

    @Override
    public final boolean isShutdown() {
        return shutdown;
    }

    @Override
    public <T> MessageChannel<T> createOutputChannel(String channelId, Class<T> messageType,
                                                     PropertyAccessor propertyAccessor) {
        String resolvedPath = expandPlaceholders(channelId, propertyAccessor);
        Path path = Path.of(resolvedPath);
        var baseFileName = getBaseFileName(path);
        try {
            var writer = createWriter(path);
            if (baseFileName.endsWith(".jsonl"))
                return new JsonlResourceMessageChannel<>(writer);
            else if (baseFileName.endsWith(".csv"))
                return new CsvResourceMessageChannel<>(writer, messageType, true);
            else
                throw new IllegalArgumentException("Unsupported file extension: " + baseFileName);

        } catch (IOException e) {
            throw new MessageChannelException("Failed to create writer for path: " + path, e);
        }
    }

    /**
     * Returns the base file name without compression extensions.
     * For example, "data.jsonl.gz" becomes "data.jsonl".
     */
    protected String getBaseFileName(Path path) {
        var fileName = path.getFileName().toString();
        var fileNameLower = fileName.toLowerCase();
        if (fileNameLower.endsWith(".gz"))
            return fileName.substring(0, fileName.length() - ".gz".length());
        else if (fileNameLower.endsWith(".zip"))
            return fileName.substring(0, fileName.length() - ".zip".length());

        return fileName;
    }

    /**
     * Creates a Writer for the given path. If the file name indicates compression,
     * the output stream is wrapped accordingly.
     */
    protected Writer createWriter(Path path) throws IOException {
        var fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".gz")) {
            return new OutputStreamWriter(
                    new GZIPOutputStream(
                            Files.newOutputStream(path, CREATE, TRUNCATE_EXISTING)
                    ), UTF_8);
        } else if (fileName.endsWith(".zip")) {
            ZipOutputStream zos = new ZipOutputStream(
                    Files.newOutputStream(path, CREATE, TRUNCATE_EXISTING)
            );
            String baseName = getBaseFileName(path);
            zos.putNextEntry(new ZipEntry(baseName));
            return new OutputStreamWriter(zos, UTF_8);
        } else {
            return Files.newBufferedWriter(path, UTF_8, CREATE, TRUNCATE_EXISTING);
        }
    }

    /**
     * Expand supported placeholders in the given file path. Currently supports:
     *   - ${now?pattern} => inserts a timestamp
     *   - ${uuid}        => inserts a random UUID
     *   - ${any.other}   => attempts to read 'any.other' from the provided PropertyAccessor (if not null)
     *
     * @param path the original file path that may contain placeholders
     * @param propertyAccessor optional accessor for reading property values
     * @return the path with placeholders expanded
     */
    protected String expandPlaceholders(String path, PropertyAccessor propertyAccessor) {
        if (path == null || !path.contains("${"))
            return path;

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(path);
        StringBuilder result = new StringBuilder();
        LocalDateTime now = null;

        while (matcher.find()) {
            String replacement;
            String placeholder = matcher.group(1);

            if (placeholder.startsWith("now?")) {
                if (now == null) {
                    now = LocalDateTime.now(); // capture once
                }
                String pattern = matcher.group(2);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                replacement = now.format(formatter);
            } else if ("uuid".equals(placeholder)) {
                replacement = UUID.randomUUID().toString();
            } else if (propertyAccessor != null) {
                try {
                    Object value = propertyAccessor.getPropertyValue(placeholder);
                    replacement = (value == null ? "" : value.toString());
                } catch (InvalidPropertyException e) {
                    replacement = matcher.group();
                }
            } else {
                replacement = matcher.group(); // unhandled placeholder, leave unchanged
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
