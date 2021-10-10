package one.chartsy.data;

import one.chartsy.Candle;

public final class CandleSupport {

    public static Candle merge(CandleSeries series) {
        var merger = new SimpleCandleBuilder();
        for (int i = series.length() - 1; i >= 0; i--)
            merger.merge(series.get(i));

        return merger.get();
    }

    private CandleSupport() {}// cannot instantiate
}