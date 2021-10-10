package one.chartsy.data.file;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.SimpleCandle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public final class SymbolResourceFiles {

    public static BufferedReader newBufferedReader(Path path, Charset cs) throws IOException {
        String fileName = path.getFileName().toString();
        if (fileName.endsWith(".gz") || fileName.endsWith(".gzip"))
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(path)), cs));
        else
            return Files.newBufferedReader(path, cs);
    }

    public static CandleSeries newCandleSeries(SymbolResource<Candle> symb, Path path) throws IOException {
        return newCandleSeries(symb, path, StandardCharsets.UTF_8);
    }

    public static CandleSeries newCandleSeries(SymbolResource<Candle> symb, Path path, Charset cs) throws IOException {
        List<Candle> candles;
        try (BufferedReader in = newBufferedReader(path, cs)) {
            candles = in.lines().map(SimpleCandle.JsonFormat::fromJson)
                    .collect(Collectors.toList());
        }
        Collections.reverse(candles);
        return CandleSeries.from(symb, candles);
    }

    public static Candle newCandle(Path path) throws IOException {
        return newCandle(path, StandardCharsets.UTF_8);
    }

    public static Candle newCandle(Path path, Charset cs) throws IOException {
        return SimpleCandle.JsonFormat.fromJson(Files.readString(path, cs));
    }

    private SymbolResourceFiles() {} // cannot instantiate
}
