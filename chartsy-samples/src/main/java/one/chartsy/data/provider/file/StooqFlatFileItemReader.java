/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.base.Dataset;
import one.chartsy.base.DoubleDataset;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.SimpleCandle;
import one.chartsy.util.Pair;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class StooqFlatFileItemReader {

    public static void main(String[] args) throws IOException {
        FileSystem fs = FileSystems.newFileSystem(Path.of("C:/Downloads/d_pl_txt(2).zip"));

        Stream<Path> files = Files.list(fs.getPath("data/daily/pl/wse stocks"));
        for (Path file : files.toList()) {
            //System.out.println(file);
            LineMapper<SimpleCandle> lineMapper = new SimpleCandleLineMapper.Type(
                    ',', Arrays.asList("SKIP","SKIP","DATE","SKIP","OPEN","HIGH","LOW","CLOSE","VOLUME"),
                    DateTimeFormatter.ofPattern("yyyyMMdd")).createLineMapper(new ExecutionContext());

            FlatFileItemReader<Candle> itemReader = new FlatFileItemReader<>();
            itemReader.setLineMapper(lineMapper);
            itemReader.setLinesToSkip(1);
            itemReader.setInputStreamSource(() -> Files.newInputStream(file));

            itemReader.open();
            Candle c, first = null, last = null;
            int count = 0;
            List<Candle> candles = new ArrayList<>();
            while ((c = itemReader.read()) != null) {
                //System.out.println(c);
                if (first == null)
                    first = c;
                last = c;
                count++;
                candles.add(c);
            }
            itemReader.close();

            System.out.println(file.getFileName() + "\t" + count + "\t" + last + "\t" + first);
            SymbolResource<Candle> resource = SymbolResource.of("ZWC", TimeFrame.Period.DAILY);
            Collections.reverse(candles);
            CandleSeries series = CandleSeries.of(resource, candles);
            System.out.println("Length: " + series.length());
            System.out.println("First: " + series.getFirst());
            System.out.println("Last: " + series.getLast());
            System.out.println(series.length());
            System.out.println(series.mapToDouble(Candle::direction).length());
            System.out.println(series.mapToDouble(Candle::direction).values().subsequences(2).length());
            System.out.println(series.mapToDouble(Candle::direction));
            System.out.println(series.mapToDouble(Candle::direction).values().subsequences(2));
            System.out.println(series.mapToDouble(Candle::direction).values().drop(1).subsequences(2));
            DoubleDataset target = series.getData().mapToDouble(Candle::direction);
            Dataset<DoubleDataset> input = series.mapToDouble(Candle::direction).values().drop(1).subsequences(2);
            System.out.println(input.withRight(target).take(100));
            System.out.println(input.withRight(target).take(100).subsequences(3));
            Dataset<Dataset<Pair<DoubleDataset, Double>>> datasets = input.withRight(target).take(100).subsequences(3);
            break;
        }
    }
}
