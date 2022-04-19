package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.data.market.Tick;

public abstract class SimpleCandleBuilder<P> extends AbstractCandleBuilder<Candle, P> {

    @Override
    public SimpleCandle get() {
        return getAsSimpleCandle();
    }

    public static SimpleCandleBuilder<Candle> fromCandles() {
        return new SimpleCandleBuilder<>() {
            @Override
            public void add(Candle c) {
                addCandle(c);
            }
        };
    }

    public static SimpleCandleBuilder<Tick> fromTicks() {
        return new SimpleCandleBuilder<>() {
            @Override
            public void add(Tick t) {
                addTick(t);
            }
        };
    }
}
