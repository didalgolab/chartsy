/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.data.function.IndexedFunction;

import java.util.function.ToIntFunction;

public class MappedDataset<E, R> extends AbstractDataset<R> {
    private final Dataset<E> origin;
    private final ToIntFunction<Dataset<E>> lengthFunction;
    private final IndexedFunction<Dataset<E>, R> getterFunction;

    public MappedDataset(Dataset<E> origin,
                         ToIntFunction<Dataset<E>> lengthFunction,
                         IndexedFunction<Dataset<E>, R> getterFunction) {
        this.origin = origin;
        this.lengthFunction = lengthFunction;
        this.getterFunction = getterFunction;
    }

    @Override
    public R get(int index) {
        return getterFunction.apply(origin, index);
    }

    @Override
    public int length() {
        return lengthFunction.applyAsInt(origin);
    }
}
