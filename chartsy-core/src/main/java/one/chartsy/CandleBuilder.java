package one.chartsy;

public interface CandleBuilder<T extends Candle, P> extends Incomplete<T> {

    void clear();

    void add(P part);
}