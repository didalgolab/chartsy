package one.chartsy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public interface TimeFrameAggregator<T, S> {

    Incomplete<T> add(S sourceItem, Consumer<T> completedItemConsumer);

    default List<T> aggregate(List<S> source) {
        return aggregate(source, true);
    }

    default List<T> aggregate(List<S> source, boolean emitLast) {
        List<T> aggregated = new ArrayList<>();
        Incomplete<T> last = null;
        for (S sourceItem : source)
            last = add(sourceItem, aggregated::add);

        if (emitLast && last != null)
            aggregated.add(last.get());

        return aggregated;
    }
}
