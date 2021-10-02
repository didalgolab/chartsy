package one.chartsy.util;

import org.openide.util.Lookup;

import java.util.function.Supplier;

public class ServiceLookup {

    public static <T> T getOrDefault(Class<T> serviceType, Supplier<T> defaultInstance) {
        T service = Lookup.getDefault().lookup(serviceType);
        return (service != null)? service: defaultInstance.get();
    }
}
