/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel;

import one.chartsy.core.Named;
import one.chartsy.core.ObjectInstantiator;
import org.openide.util.Lookup;

import java.util.HashMap;
import java.util.Map;

class LookupServiceManager<T extends Named> implements ServiceManager<T> {
    private final Class<T> serviceType;
    private volatile Map<String, ObjectInstantiator<T>> services;

    public LookupServiceManager(Class<T> serviceType) {
        this.serviceType = serviceType;
    }

    @Override
    public T get(String name) {
        var service = getServices().get(name);
        if (service == null)
            throw new IllegalArgumentException("Service `" + serviceType.getSimpleName() + '.' + name + "` not found");

        return service.newInstance();
    }

    protected Map<String, ObjectInstantiator<T>> getServices() {
        var services = this.services;
        if (services == null)
            services = this.services = loadServices(serviceType);

        return services;
    }

    protected Map<String, ObjectInstantiator<T>> loadServices(Class<T> serviceType) {
        var services = new HashMap<String, ObjectInstantiator<T>>();
        for (T service : Lookup.getDefault().lookupAll(serviceType))
            services.put(service.getName(), getInstantiatorOf(service, serviceType));

        return services;
    }

    @SuppressWarnings("unchecked")
    protected ObjectInstantiator<T> getInstantiatorOf(T service, Class<T> serviceType) {
        if (service instanceof ObjectInstantiator)
            return (ObjectInstantiator<T>)service;
        else
            return new SharedInstance<>(service);
    }

    private static final record SharedInstance<T>(T instance) implements ObjectInstantiator<T> {
        @Override
        public T newInstance() {
            return instance;
        }
    }
}
