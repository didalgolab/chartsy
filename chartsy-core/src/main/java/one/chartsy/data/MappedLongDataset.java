/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.base.function.IndexedToLongFunction;

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
