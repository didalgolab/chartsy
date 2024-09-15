/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file;

import lombok.Getter;
import lombok.Setter;
import one.chartsy.core.io.InputStreamSource;
import one.chartsy.util.CloseHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

@Getter
@Setter
public class FlatFileItemReader<T> implements AutoCloseable {

    public static final String[] NO_COMMENT_PREFIXES = new String[0];

    private InputStreamSource inputStreamSource;
    private int linesToSkip;
    private Consumer<String> skippedLinesHandler;
    private boolean ignoreEmptyLines;
    private boolean stripLines;
    private String[] commentPrefixes = NO_COMMENT_PREFIXES;
    private String encoding = "UTF-8";
    private LineMapper<? extends T> lineMapper;

    private BufferedReader lineReader;
    private int lineCount;

    public FlatFileItemReader() { }

    public FlatFileItemReader(FlatFileFormat fileFormat) {
        setLinesToSkip(fileFormat.getSkipFirstLines());
        setEncoding(fileFormat.getEncoding());
        setStripLines(fileFormat.isStripLines());
        setIgnoreEmptyLines(fileFormat.isIgnoreEmptyLines());
    }

    public void open() {
        if (lineReader != null)
            throw new IllegalStateException("FlatFileReader already open");
        if (lineMapper == null)
            throw new IllegalStateException("LineMapper not set");
        if (inputStreamSource == null)
            throw new IllegalStateException("InputStreamSource not set");

        try {
            lineCount = 0;
            lineReader = createLineReader(inputStreamSource);
            for (int i = 0; i < linesToSkip; i++) {
                String line = readLine();
                if (skippedLinesHandler != null)
                    skippedLinesHandler.accept(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean isOpen() {
        return lineReader != null;
    }

    protected BufferedReader createLineReader(InputStreamSource iss) throws IOException {
        return new BufferedReader(new InputStreamReader(requireNonNull(iss.getInputStream()), encoding));
    }

    protected boolean isCommentLine(String line) {
        for (String prefix : commentPrefixes)
            if (line.startsWith(prefix))
                return true;

        return false;
    }

    protected String readLine() throws IOException {
        while (true) {
            String line = lineReader.readLine();
            if (line == null)
                return null;

            lineCount++;
            if (stripLines)
                line = line.strip();
            if (isCommentLine(line) || ignoreEmptyLines && line.isEmpty())
                continue;
            return line;
        }
    }

    public T read() throws IOException {
        String line = readLine();
        if (line == null)
            return null;

        try {
            return lineMapper.mapLine(line, lineCount);
        } catch (FlatFileParseException e) {
            throw e;
        } catch (Exception e) {
            throw new FlatFileParseException("Unable to parse line", e, line, lineCount);
        }
    }

    public List<T> readAll() throws IOException {
        List<T> resultList = new ArrayList<>();
        T item;
        while ((item = read()) != null)
            resultList.add(item);

        return resultList;
    }

    @Override
    public void close() {
        CloseHelper.closeQuietly(lineReader);
        this.lineReader = null;
    }
}
