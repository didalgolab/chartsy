package one.chartsy.data.provider.file;

import one.chartsy.*;
import one.chartsy.collections.DoubleArray;
import one.chartsy.core.collections.DoubleMinMaxList;
import one.chartsy.data.*;
import one.chartsy.data.batch.Batches;
import one.chartsy.data.packed.PackedCandleSeries;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.finance.FinancialIndicators;
import one.chartsy.util.Pair;
import org.apache.commons.math3.random.EmpiricalDistribution;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.LineMapper;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

public class StooqFlatFileDataProvider {

    public static void main(String[] args) throws IOException {
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Users/Mariusz/Downloads/d_pl_txt(3).zip"));

        DataQuery<Candle> query = DataQuery.of(
                SymbolResource.of("BIO", TimeFrame.Period.DAILY));

        //CandleSeries series = dataProvider.queryForCandles(query).collect(Batches.toCandleSeries());
        Map<Double, String> counts = new TreeMap<>();
        List<SymbolIdentity> stocks = dataProvider.getSymbolList(new SymbolGroup("/data/daily/pl/wse stocks"));
        System.out.println("Stocks: " + stocks.size());
        for (SymbolIdentity stock : stocks) {
            CandleSeries series = dataProvider.queryForCandles(
                            DataQuery.resource(SymbolResource.of(stock, TimeFrame.Period.DAILY)).limit(170).build())
                    .collect(Batches.toCandleSeries());

            if (series.length() == 0) {
                System.out.println("Empty series: " + stock);
                continue;
            }
            DoubleMinMaxList bands = FinancialIndicators.Sfora.bands(PackedCandleSeries.from(series));
            DoubleSeries width = bands.getMaximum().sub(bands.getMinimum());
            if (width.length() == 0)
                continue;

            double lastClose = series.getLast().close();
            double widthPercent = width.getLast() / lastClose;
            int n = 5_000, cnt = 0;
            for (int i = 0; i < n; i++) {
                Series<Candle> newSeries = series.bootstrap();

                DoubleMinMaxList newBands = FinancialIndicators.Sfora.bands(PackedCandleSeries.from(newSeries));
                DoubleSeries newWidth = newBands.getMaximum().sub(newBands.getMinimum());
                double newLastClose = newSeries.getLast().close();
                double newWidthPercent = newWidth.getLast() / newLastClose;
                if (newWidthPercent < widthPercent)
                    cnt++;
            }
            counts.put((cnt*10_000L/n)/100.0, stock.name());

            System.out.println("" + stock.name() + ": " + (cnt*10_000L/n)/100.0 + " %");
        }
        counts.forEach((k,v) -> System.out.println("# " + k + ": " + v));

        if (true)
            return;

        Series<Candle> s = dataProvider.queryForCandles(query).collect(Batches.toSeries());
        System.out.println(s.length());
        System.out.println(dataProvider.getSubGroups(new SymbolGroup("/data/daily/pl/wse stocks")));
        for (SymbolIdentity symbol : stocks) {
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
            CandleSeries series2 = CandleSeries.of(resource, candles);
            System.out.println("Length: " + series2.length());
            System.out.println("First: " + series2.getFirst());
            System.out.println("Last: " + series2.getLast());
            System.out.println(series2.length());
            System.out.println(series2.mapToDouble(Candle::signum).length());
            System.out.println(series2.mapToDouble(Candle::signum).values().subsequences(2).length());
            System.out.println(series2.mapToDouble(Candle::signum));
            System.out.println(series2.mapToDouble(Candle::signum).values().subsequences(2));
            System.out.println(series2.mapToDouble(Candle::signum).values().ref(-1).subsequences(2));
            DoubleDataset target = series2.getData().mapToDouble(Candle::signum);
            Dataset<DoubleDataset> input = series2.mapToDouble(Candle::signum).values().ref(-1).subsequences(2);
            System.out.println(input.withRight(target).take(0, 100));
            System.out.println(input.withRight(target).take(0, 100).subsequences(3));
            Dataset<Dataset<Pair<DoubleDataset, Double>>> datasets = input.withRight(target).take(0, 100).subsequences(3);
            break;
        }
    }
}
