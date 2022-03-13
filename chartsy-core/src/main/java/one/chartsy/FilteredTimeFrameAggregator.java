package one.chartsy;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class FilteredTimeFrameAggregator<T, E> implements TimeFrameAggregator<T, E> {

    private final Predicate<E> filter;
    private final TimeFrameAggregator<T, E> target;
    private Incomplete<T> result = Incomplete.empty();

    public FilteredTimeFrameAggregator(Predicate<E> filter, TimeFrameAggregator<T, E> target) {
        this.filter = filter;
        this.target = target;
    }

    @Override
    public Incomplete<T> add(E element, Consumer<T> completedItemConsumer) {
        return filter.test(element)? (result = target.add(element, completedItemConsumer)) : result;
    }
}
