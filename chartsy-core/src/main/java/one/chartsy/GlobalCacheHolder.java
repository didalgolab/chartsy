/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

class GlobalCacheHolder {
    static final GlobalCacheHolder INSTANCE = new GlobalCacheHolder();
    private final Map<Object, Object> cache = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T getOrDefaultStronglyCached(Class<T> type, Supplier<T> defaultSupplier) {
        return (T) cache.computeIfAbsent(type, key -> defaultSupplier.get());
    }
}
