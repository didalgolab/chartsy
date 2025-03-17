/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file;

import lombok.Builder;
import lombok.Getter;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.time.Chronological;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Getter
@Builder(builderClassName = "Builder")
public class FlatFileFormat {
    private final String fileNamePattern;
    private final Chronological.ChronoOrder dataOrder;
    private final String encoding;
    private final int skipFirstLines;
    private final boolean ignoreEmptyLines;
    private final boolean stripLines;
    private final boolean caseSensitiveSymbols;
    private final LineMapperType<?> lineMapper;

    public static class Builder {
        Chronological.ChronoOrder dataOrder = Chronological.ChronoOrder.CHRONOLOGICAL;
        String encoding = "ISO-8859-2";
    }

    public static final FlatFileFormat FOREXTESTER = builder()
            .fileNamePattern("*\\.zip")
            .skipFirstLines(1)
            .lineMapper(
                    new SimpleCandleLineMapper.Type(
                            ',', Arrays.asList("SKIP","DATE","TIME","OPEN","HIGH","LOW","CLOSE","SKIP"), DateTimeFormatter.ofPattern("yyyyMMdd"), DateTimeFormatter.ofPattern("HHmmss")))
            .build();

    public static final FlatFileFormat HISTDATA_ASCII = builder()
            .fileNamePattern("*\\.zip")
            .lineMapper(
                    new SimpleCandleLineMapper.Type(
                            ';', Arrays.asList("OPEN_DATE_TIME","OPEN","HIGH","LOW","CLOSE","VOLUME"), DateTimeFormatter.ofPattern("yyyyMMdd HHmmss")))
            .build();

    public static final FlatFileFormat STOOQ = builder()
            .fileNamePattern("*\\.zip")
            .lineMapper(
                    new SimpleCandleLineMapper.Type(
                            ',', Arrays.asList("SKIP","SKIP","DATE","TIME","OPEN","HIGH","LOW","CLOSE","VOLUME"), DateTimeFormatter.ofPattern("yyyyMMdd"), DateTimeFormatter.ofPattern("HHmmss")))
            .skipFirstLines(1)
            .build();

    public FlatFileDataProvider newDataProvider(String file) throws IOException {
        return newDataProvider(Path.of(file));
    }

    public FlatFileDataProvider newDataProvider(Path file) throws IOException {
        return new FlatFileDataProvider(this, file);
    }

}
