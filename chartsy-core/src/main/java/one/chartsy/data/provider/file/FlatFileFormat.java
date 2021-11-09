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
    private final Chronological.Order dataOrder;
    private final String encoding;
    private final int skipFirstLines;
    private final boolean ignoreEmptyLines;
    private final boolean stripLines;
    private final LineMapperType<?> lineMapper;

    public static class Builder {
        Chronological.Order dataOrder = Chronological.Order.CHRONOLOGICAL;
        String encoding = "ISO-8859-2";
    }

    public static final FlatFileFormat STOOQ = builder()
            .fileNamePattern("*\\.zip")
            .lineMapper(
                    new SimpleCandleLineMapper.Type(
                            ',', Arrays.asList("SKIP","SKIP","DATE","SKIP","OPEN","HIGH","LOW","CLOSE","VOLUME"), DateTimeFormatter.ofPattern("yyyyMMdd")))
            .skipFirstLines(1)
            .build();

    public FlatFileDataProvider newDataProvider(Path file) throws IOException {
        return new FlatFileDataProvider(this, file);
    }

}
