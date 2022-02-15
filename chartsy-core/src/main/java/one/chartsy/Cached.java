package one.chartsy;

import java.util.function.Supplier;

public interface Cached {

    static <T> T get(Class<T> type, Supplier<T> defaultSupplier) {
        return GlobalCacheHolder.INSTANCE.getOrDefaultStronglyCached(type, defaultSupplier);
    }
}
