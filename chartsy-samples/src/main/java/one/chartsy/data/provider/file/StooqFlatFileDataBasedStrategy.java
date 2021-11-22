package one.chartsy.data.provider.file;

import one.chartsy.*;
import one.chartsy.core.collections.DoubleMinMaxList;
import one.chartsy.data.*;
import one.chartsy.data.batch.Batches;
import one.chartsy.data.packed.PackedCandleSeries;
import one.chartsy.data.provider.DataProviders;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.finance.FinancialIndicators;
import one.chartsy.simulation.SimulationContext;
import one.chartsy.simulation.SimulationRunner;
import one.chartsy.simulation.TradingSimulator;
import one.chartsy.simulation.engine.SimpleSimulationRunner;
import one.chartsy.time.Chronological;
import one.chartsy.trade.MetaStrategy;
import one.chartsy.trade.Strategy;
import one.chartsy.util.Pair;
import org.openide.util.Lookup;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class StooqFlatFileDataBasedStrategy {

    public static void main(String[] args) throws IOException {
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Users/Mariusz/Downloads/d_pl_txt(5).zip"));

        List<? extends SymbolIdentity> stocks = dataProvider.listSymbols(new SymbolGroup("/data/daily/pl/wse stocks"));
        List<CandleSeries> seriesList = DataProviders.getHistoricalCandles(dataProvider, TimeFrame.Period.DAILY, stocks);
        System.out.println(seriesList.size());


        AtomicInteger strategyInstanceCount = new AtomicInteger();
        AtomicInteger dataPointCount = new AtomicInteger();
        class MyStrategy extends Strategy<Candle> {
            private MetaStrategy metaStrategy = lookup(MetaStrategy.class);
            private final Set<LocalDate> dates = globalVariable("dates", HashSet::new);

            MyStrategy() {
                strategyInstanceCount.incrementAndGet();
            }

            @Override
            public void entryOrders(When when, Chronological data) {
                dataPointCount.incrementAndGet();

            }

            @Override
            public void onTradingDayStart(LocalDate date) {
                super.onTradingDayStart(date);
                if (dates.add(date)) {
                    System.out.println(date + " " + metaStrategy.activeSymbolCountSince(Chronological.toEpochMicros(date.atStartOfDay().minusDays(14))));
                }
            }
        }
        MetaStrategy metaStrategy = new MetaStrategy(MyStrategy::new);
        TradingSimulator simulator = new TradingSimulator(metaStrategy);
        SimulationContext context = Lookup.getDefault().lookup(SimulationContext.class);
        SimulationRunner runner = new SimpleSimulationRunner(context);
        runner.run(seriesList, simulator);

        System.out.println("Strategies:" + strategyInstanceCount);
        System.out.println("DataPoints:" + dataPointCount);

        if (true)
            return;

        //CandleSeries series = dataProvider.queryForCandles(query).collect(Batches.toCandleSeries());
        Map<Pair<Double, String>, String> counts = new TreeMap<>();
        List<? extends SymbolIdentity> stocks2 = dataProvider.listSymbols(new SymbolGroup("/data/daily/pl/wse stocks"));
        System.out.println("Stocks: " + stocks.size());
        for (SymbolIdentity stock : stocks) {
            DataQuery<Candle> query = DataQuery.resource(SymbolResource.of(stock, TimeFrame.Period.DAILY))
                    .limit(250)
                    .endTime(LocalDateTime.of(2021, 10, 1, 0, 0))
                    .build();
            CandleSeries series = dataProvider.queryForCandles(query).collect(Batches.toCandleSeries());

            if (series.length() == 0) {
                System.out.println("Empty series: " + stock);
                continue;
            }
            DoubleMinMaxList bands = FinancialIndicators.Sfora.bands(PackedCandleSeries.from(series));
            DoubleSeries width = bands.getMaximum().sub(bands.getMinimum());
            DoubleSeries highestSince = PackedCandleSeries.from(series).highestSince();
            if (width.length() == 0)
                continue;

            double lastClose = series.getLast().close();
            double widthLast = width.getLast();
            double widthPercent = width.getLast() / lastClose;
            System.out.println("STOCK: " + stock.name() + " - " + series.getLast() + ": HighestSince=" + widthLast);
            int n = 1_000, cnt = 0;
            for (int i = 0; i < n; i++) {
                Series<Candle> newSeries = series.resample(AdjustmentMethod.RELATIVE);

                DoubleMinMaxList newBands = FinancialIndicators.Sfora.bands(PackedCandleSeries.from(newSeries));
                DoubleSeries newWidth = newBands.getMaximum().sub(newBands.getMinimum());
                DoubleSeries newHighestSince = PackedCandleSeries.from(newSeries).highestSince();
                double newLastClose = newSeries.getLast().close();
                double newWidthLast = newWidth.getLast();
                double newWidthPercent = newWidth.getLast() / newLastClose;
                if (newWidthPercent < widthPercent && newHighestSince.getLast() >= highestSince.getLast())
                    cnt++;
            }
            counts.put(Pair.of((cnt*10_000L/n)/100.0, stock.name()), stock.name());

            System.out.println("" + stock.name() + ": " + (cnt*10_000L/n)/100.0 + " %");
        }
        counts.forEach((k,v) -> System.out.println("# " + k + ": " + v));
    }
}
