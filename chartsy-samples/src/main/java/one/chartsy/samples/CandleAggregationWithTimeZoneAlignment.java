package one.chartsy.samples;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.file.SymbolResourceFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CandleAggregationWithTimeZoneAlignment {

    public static void main(String[] args) throws IOException {
        CandleSeries series = SymbolResourceFiles.newCandleSeries(SymbolResource.of("EURUSD", TimeFrame.Period.H1), Path.of("C:/Work/Data/Forex/EURUSD.COMP.gz"));
        List<Candle> list = new ArrayList<>(series.getData().toImmutableList());
        Collections.reverse(list);

        TimeFrame timeFrame = TimeFrame.Period.DAILY.withTimeZone(ZoneId.of("Europe/Warsaw"));
        List<Candle> dailyList = timeFrame.getAggregator()
                .aggregate(list);
        Collections.reverse(dailyList);
        CandleSeries daily = CandleSeries.of(series.getResource().withTimeFrame(timeFrame), dailyList);

        System.out.println("Count: " + series.length());
        System.out.println("First: " + series.getFirst());
        System.out.println("Last: " + series.getLast());

        for (int i = 0; i <= 300; i++)
            System.out.println("#" + i + ": " + dailyList.get(i));
        for (int i = 0; i <= 8; i++)
            System.out.println("Last: " + dailyList.get(dailyList.size()-i-1));
    }
}
