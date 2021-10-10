package one.chartsy.data.market;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.Incomplete;
import one.chartsy.TimeFrameAggregator;

import java.util.function.Consumer;

public abstract class AbstractCandleAggregator<T extends Candle, E> implements TimeFrameAggregator<T, E> {

    private final CandleBuilder<T, E> candle;


    public AbstractCandleAggregator(CandleBuilder<T, E> builder) {
        this.candle = builder;
    }

    protected abstract boolean isCompletedBy(E element);

    @Override
    public Incomplete<T> add(E element, Consumer<T> completedItemConsumer) {
        if (isCompletedBy(element) && candle.isPresent()) {
            completedItemConsumer.accept(candle.get());
            candle.put(element);
        } else {
            candle.merge(element);
        }
        return candle;
    }
}
