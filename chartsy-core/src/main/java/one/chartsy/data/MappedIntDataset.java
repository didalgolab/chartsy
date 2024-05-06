/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.base.function.IndexedToIntFunction;

import java.util.function.ToIntFunction;

public class MappedIntDataset extends AbstractIntDataset {
    private final IntDataset origin;
    private final ToIntFunction<IntDataset> lengthFunction;
    private final IndexedToIntFunction<IntDataset> getterFunction;

    public MappedIntDataset(IntDataset origin,
                            ToIntFunction<IntDataset> lengthFunction,
                            IndexedToIntFunction<IntDataset> getterFunction) {
        this.origin = origin;
        this.lengthFunction = lengthFunction;
        this.getterFunction = getterFunction;
    }

    @Override
    public int get(int index) {
        return getterFunction.applyAsInt(origin, index);
    }

    @Override
    public int length() {
        return lengthFunction.applyAsInt(origin);
    }
}
