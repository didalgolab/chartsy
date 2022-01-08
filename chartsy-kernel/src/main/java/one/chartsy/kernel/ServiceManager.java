package one.chartsy.kernel;

import one.chartsy.core.Named;
import org.openide.util.Lookup;

public interface ServiceManager<T extends Named> {

    static <T extends Named> ServiceManager<T> of(Class<T> type) {
        var provider = Lookup.getDefault().lookup(ServiceManagerProvider.class);
        return provider.getServiceManager(type);
    }

    T get(String name);
}
