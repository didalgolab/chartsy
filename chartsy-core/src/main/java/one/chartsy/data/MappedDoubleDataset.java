/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.base.function.IndexedToDoubleFunction;

import java.util.function.ToIntFunction;

public class MappedDoubleDataset extends AbstractDoubleDataset {
    private final DoubleDataset origin;
    private final ToIntFunction<DoubleDataset> lengthFunction;
    private final IndexedToDoubleFunction<DoubleDataset> getterFunction;

    public MappedDoubleDataset(DoubleDataset origin,
                               ToIntFunction<DoubleDataset> lengthFunction,
                               IndexedToDoubleFunction<DoubleDataset> getterFunction) {
        this.origin = origin;
        this.lengthFunction = lengthFunction;
        this.getterFunction = getterFunction;
    }

    @Override
    public double get(int index) {
        return getterFunction.applyAsDouble(origin, index);
    }

    @Override
    public int length() {
        return lengthFunction.applyAsInt(origin);
    }
}
