package one.chartsy.data.packed;

import one.chartsy.data.AbstractDataset;
import one.chartsy.data.Dataset;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

public class PackedDataset<E> extends AbstractDataset<E> {

    public static final PackedDataset<?> EMPTY = new PackedDataset<>(new Object[0]);

    private final E[] values;

    protected PackedDataset(E[] values) {
        this.values = values;
    }

    public static <E> PackedDataset<E> of(E[] values) {
        return new PackedDataset<>(values.clone());
    }

    public static <E> PackedDataset<E> of(Collection<? extends E> values) {
        return new PackedDataset<>(values.toArray((E[])new Object[values.size()]));
    }

    public static <E> PackedDataset<E> from(Dataset<E> dataset) {
        if (dataset instanceof PackedDataset)
            return (PackedDataset<E>) dataset;

        @SuppressWarnings("unchecked")
        E[] values = (E[]) new Object[dataset.length()];
        Arrays.setAll(values, dataset::get);
        return new PackedDataset<>(values);
    }

    @Override
    public E get(int index) {
        return values[index];
    }

    @Override
    public int length() {
        return values.length;
    }

    @Override
    public Stream<E> stream() {
        return Arrays.stream(values);
    }
}
