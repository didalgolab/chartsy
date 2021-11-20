package one.chartsy.data.provider.file.stooq;

import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.DataQuery;
import one.chartsy.data.SeriesSummary;
import one.chartsy.data.batch.Batches;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class SeriesSummaryUSExample {

    public static void main(String[] args) throws IOException {
        // create FlatFileDataProvider for a Stooq historical data file
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Users/Mariusz/Downloads/d_us_txt.zip"));

        // list all symbol groups containing stock symbols
        List<SymbolGroup> stockGroups = dataProvider.listSymbolGroups(
                group -> group.name().contains("stocks") && !group.name().contains("nysemkt"));
        System.out.println("Stock groups: " + stockGroups);

        // list all stocks contained in a file
        List<? extends SymbolIdentity> stocks = dataProvider.listSymbols(stockGroups);
        int stockCount = stocks.size();
        System.out.printf("Found %d stock(s)".replace("(s)", stockCount==1?"":"s"), stockCount);
        System.out.println();

        // list summary of each stock data series
        int candleCount = 0;
        for (SymbolIdentity stock : stocks) {
            var summary = dataProvider.queryForCandles(
                            DataQuery.of(SymbolResource.of(stock, TimeFrame.Period.DAILY)))
                    .collect(Batches.toCandleSeries())
                    .query(SeriesSummary::new);
            candleCount += summary.getCount();
            System.out.println(summary);
        }
        System.out.printf("Total %d candle(s)".replace("(s)", candleCount==1?"":"s"), candleCount);
    }
}
