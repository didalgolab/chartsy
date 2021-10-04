package one.chartsy;

public interface CandleBuilder<T extends Candle> extends Incomplete<T> {

    void put(T candle);

    void merge(T candle);
}
