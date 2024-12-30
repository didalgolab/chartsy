/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel;

import one.chartsy.core.Named;
import org.openide.util.lookup.ServiceProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServiceProvider(service = ServiceManagerProvider.class)
public class ServiceManagerProvider {

    private final Map<Class<?>, ServiceManager<?>> registry = new ConcurrentHashMap<>();

    public <T extends Named> ServiceManager<T> getServiceManager(Class<T> type) {
        @SuppressWarnings("unchecked")
        var manager = (ServiceManager<T>) registry.computeIfAbsent(type, t -> new LookupServiceManager<>((Class<T>)t));
        return manager;
    }
}
