package one.chartsy.core;

import one.chartsy.Incomplete;
import one.chartsy.TimeFrameAggregator;

public class DefaultTimeFrameServices {

    private static final TimeFrameAggregator<?, ?> passThroughAggregator = (sourceItem, completedItemConsumer) -> {
        completedItemConsumer.accept(sourceItem);
        return Incomplete.empty();
    };

    public <T> TimeFrameAggregator<T, T> createPassThroughAggregator() {
        @SuppressWarnings("unchecked")
        TimeFrameAggregator<T, T> agg = (TimeFrameAggregator<T, T>) passThroughAggregator;
        return agg;
    }
}
