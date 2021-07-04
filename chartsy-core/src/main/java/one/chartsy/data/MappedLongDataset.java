package one.chartsy.data;

import one.chartsy.data.function.IndexedToLongFunction;

import java.util.function.ToIntFunction;

public class MappedLongDataset implements LongDataset {
    private final LongDataset origin;
    private final ToIntFunction<LongDataset> lengthFunction;
    private final IndexedToLongFunction<LongDataset> getterFunction;

    public MappedLongDataset(LongDataset origin,
                             ToIntFunction<LongDataset> lengthFunction,
                             IndexedToLongFunction<LongDataset> getterFunction) {
        this.origin = origin;
        this.lengthFunction = lengthFunction;
        this.getterFunction = getterFunction;
    }

    @Override
    public long get(int index) {
        return getterFunction.applyAsLong(origin, index);
    }

    @Override
    public int length() {
        return lengthFunction.applyAsInt(origin);
    }
}
