/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import java.util.function.Supplier;

public interface Cached {

    static <T> T get(Class<T> type, Supplier<T> defaultSupplier) {
        return GlobalCacheHolder.INSTANCE.getOrDefaultStronglyCached(type, defaultSupplier);
    }
}
