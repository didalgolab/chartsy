package one.chartsy.data.provider.file;

import one.chartsy.*;
import one.chartsy.data.*;
import one.chartsy.data.batch.Batches;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.util.Pair;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.LineMapper;

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

public class StooqFlatFileDataProvider {

    public static void main(String[] args) throws IOException {
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Users/Mariusz/Downloads/d_pl_txt(2).zip"));

        DataQuery<Candle> query = DataQuery
                .resource(SymbolResource.of("bio", TimeFrame.Period.DAILY))
                .build();

        Series<Candle> s = dataProvider.queryForCandles(query).collect(Batches.toSeries());
        System.out.println(s.length());
        System.out.println(dataProvider.getSubGroups(new SymbolGroup("/data/daily/pl/wse stocks")));
        for (SymbolIdentity symbol : dataProvider.getSymbolList(new SymbolGroup("/data/daily/pl/wse stocks"))) {
            System.out.println(symbol.name());
            System.out.println(dataProvider
                    .queryForCandles(DataQuery.of(SymbolResource.of(symbol, TimeFrame.Period.DAILY)))
                    .collect(Batches.toSeries())
                    .length()
            );
        }

        //System.out.println(dataProvider.getSymbolFiles());
        if (true)
            return;

        FileSystem fs = FileSystems.newFileSystem(Path.of("C:/Users/Mariusz/Downloads/d_pl_txt(2).zip"));

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
            System.out.println(series.mapToDouble(Candle::signum).length());
            System.out.println(series.mapToDouble(Candle::signum).values().subsequences(2).length());
            System.out.println(series.mapToDouble(Candle::signum));
            System.out.println(series.mapToDouble(Candle::signum).values().subsequences(2));
            System.out.println(series.mapToDouble(Candle::signum).values().ref(-1).subsequences(2));
            DoubleDataset target = series.getData().mapToDouble(Candle::signum);
            Dataset<DoubleDataset> input = series.mapToDouble(Candle::signum).values().ref(-1).subsequences(2);
            System.out.println(input.withRight(target).take(0, 100));
            System.out.println(input.withRight(target).take(0, 100).subsequences(3));
            Dataset<Dataset<Pair<DoubleDataset, Double>>> datasets = input.withRight(target).take(0, 100).subsequences(3);
            break;
        }
    }
}
