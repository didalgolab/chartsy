/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.api.messages.handlers.ShutdownResponseHandler;
import one.chartsy.core.event.ListenerList;
import one.chartsy.data.stream.MessageChannel;
import one.chartsy.data.stream.MessageChannelException;
import one.chartsy.kernel.data.stream.csv.CsvResourceMessageChannel;
import one.chartsy.kernel.data.stream.json.JsonlResourceMessageChannel;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public abstract class AbstractAlgorithmContext implements AlgorithmContext {
    private final ListenerList<ShutdownResponseHandler> shutdownResponseHandlers = ListenerList.of(ShutdownResponseHandler.class);
    private final String name;
    private volatile boolean shutdown;


    protected AbstractAlgorithmContext(String name) {
        this.name = name;
        addShutdownResponseHandler(__ -> this.shutdown = true);
    }

    @Override
    public final String getName() {
        return name;
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
    public <T> MessageChannel<T> createOutputChannel(Path path, Class<T> messageType) {
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
}
