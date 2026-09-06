/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider;

import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.data.provider.file.FlatFileItemReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/** Retains a small tail while streaming through compressed entries, without allocating candle histories. */
final class FlatFileLastRecordReader {
    private final byte[] block = new byte[64 * 1024];
    private final byte[] tail = new byte[2 * 1024];

    String read(Path file, FlatFileFormat format) throws IOException {
        long totalBytes = readTail(file);
        if (totalBytes <= tail.length)
            return readLastRecord(new ByteArrayInputStream(tail, 0, (int) totalBytes), format);

        String lastRecord = lastCompleteRecordInTail(format);
        if (lastRecord != null)
            return lastRecord;

        // Oversized records or trailing whitespace may leave no complete record in the tail.
        // A second text-only pass preserves the format's exact header and empty-line semantics.
        return readLastRecord(Files.newInputStream(file), format);
    }

    private long readTail(Path file) throws IOException {
        int retained = 0;
        long total = 0;
        try (var input = Files.newInputStream(file)) {
            for (int count; (count = input.read(block)) != -1;) {
                if (Thread.currentThread().isInterrupted())
                    throw new IOException("Endpoint reading interrupted");
                if (count == 0)
                    continue;
                total += count;
                if (count >= tail.length) {
                    System.arraycopy(block, count - tail.length, tail, 0, tail.length);
                    retained = tail.length;
                } else {
                    int keep = Math.min(retained, tail.length - count);
                    System.arraycopy(tail, retained - keep, tail, 0, keep);
                    System.arraycopy(block, 0, tail, keep, count);
                    retained = keep + count;
                }
            }
        }
        return total;
    }

    private String lastCompleteRecordInTail(FlatFileFormat format) {
        int end = tail.length;
        while (end > 0) {
            // BufferedReader treats CR, LF and CRLF as one terminator, including at EOF.
            if (tail[end - 1] == '\n') {
                end--;
                if (end > 0 && tail[end - 1] == '\r')
                    end--;
            } else if (tail[end - 1] == '\r') {
                end--;
            }
            int start = end;
            while (start > 0 && tail[start - 1] != '\n' && tail[start - 1] != '\r')
                start--;
            if (start == 0)
                break; // The record may begin outside the retained tail.
            String line = new String(tail, start, end - start, Charset.forName(format.getEncoding()));
            if (format.isStripLines())
                line = line.strip();
            if (!format.isIgnoreEmptyLines() || !line.isEmpty())
                return line;
            end = start;
        }
        return null;
    }

    private String readLastRecord(InputStream input, FlatFileFormat format) throws IOException {
        try (var reader = new FlatFileItemReader<String>(format)) {
            reader.setInputStreamSource(() -> input);
            reader.setLineMapper((line, number) -> line);
            reader.open();
            String last = null;
            for (String line; (line = reader.read()) != null;) {
                if (Thread.currentThread().isInterrupted())
                    throw new IOException("Endpoint reading interrupted");
                last = line;
            }
            return last;
        }
    }
}
