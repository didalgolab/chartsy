package one.chartsy;

import java.util.function.ToDoubleFunction;

public enum CandleField implements FinancialField {
    TIME (Candle::getTime),
    OPEN (Candle::open),
    HIGH (Candle::high),
    LOW (Candle::low),
    CLOSE (Candle::close),
    VOLUME (Candle::volume),
    COUNT (Candle::count),
    ;

    CandleField(ToDoubleFunction<Candle> doubleValue) {
        this.doubleValue = doubleValue;
    }

    private final ToDoubleFunction<Candle> doubleValue;

    public double getFrom(Candle c) {
        return doubleValue.applyAsDouble(c);
    }
}
