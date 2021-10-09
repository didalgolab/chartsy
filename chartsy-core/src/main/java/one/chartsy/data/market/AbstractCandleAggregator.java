package one.chartsy.data.market;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.Incomplete;
import one.chartsy.TimeFrameAggregator;

import java.util.function.Consumer;

public abstract class AbstractCandleAggregator<E> implements TimeFrameAggregator<Candle, E> {

    private final CandleBuilder<Candle, E> candle;


    public AbstractCandleAggregator(CandleBuilder<Candle, E> builder) {
        this.candle = builder;
    }

    protected abstract boolean isCompletedBy(E element);

    @Override
    public Incomplete<Candle> add(E element, Consumer<Candle> completedItemConsumer) {
        if (isCompletedBy(element) && candle.isPresent()) {
            completedItemConsumer.accept(candle.get());
            candle.put(element);
        } else {
            candle.merge(element);
        }
        return candle;
    }
}
