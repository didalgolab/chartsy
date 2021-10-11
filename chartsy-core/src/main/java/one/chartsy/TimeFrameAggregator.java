package one.chartsy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public interface TimeFrameAggregator<T, E> {

    Incomplete<T> add(E element, Consumer<T> completedItemConsumer);

    default List<T> aggregate(List<? extends E> elements) {
        return aggregate(elements, true);
    }

    default List<T> aggregate(List<? extends E> elements, boolean emitLast) {
        List<T> aggregated = new ArrayList<>();
        Incomplete<T> last = null;
        for (E element : elements)
            last = add(element, aggregated::add);

        if (emitLast && last != null)
            aggregated.add(last.get());

        return aggregated;
    }
}
