/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.service.loader;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Central support for locating services in the system.
 *
 * <p><strong>Singleton:</strong> {@link #INSTANCE} is resolved via the JDK
 * {@link java.util.ServiceLoader}. Applications may provide a custom subclass
 * by registering it under
 * {@code META-INF/services/one.chartsy.service.loader.ServiceLoaderSupport}.
 * If no subclass is registered, the base implementation is used.
 *
 * <h2>Lookup strategy</h2>
 * <ol>
 *   <li><b>Primary:</b> NetBeans {@code org.openide.util.Lookup}
 *       ({@code Lookup.getDefault().lookup(type)}), if NetBeans Lookup is on the classpath.
 *       This enables discovery of services registered via {@code @ServiceProvider}, etc.</li>
 *   <li><b>Fallback:</b> JDK {@link java.util.ServiceLoader} using
 *       {@code META-INF/services/&lt;FQN-of-service&gt;}.</li>
 * </ol>
 *
 * <h2>Failure behavior</h2>
 * This API returns {@link Optional}; callers decide whether to supply a default,
 * throw, or take other action (e.g., {@code findService(type).orElseThrow(...)}).
 *
 * <h2>Thread-safety</h2>
 * The singleton is safely published by JVM class initialization; instances are stateless.
 *
 * <h2>Providing a custom support implementation</h2>
 * Register your subclass first in the provider file:
 * <pre>
 *   # META-INF/services/one.chartsy.service.loader.ServiceLoaderSupport
 *   com.acme.infrastructure.CustomServiceLoaderSupport
 *   one.chartsy.service.loader.ServiceLoaderSupport
 * </pre>
 * The support chooses a registered subclass over the base implementation.
 */
@ServiceProvider(service = ServiceLoaderSupport.class)
public class ServiceLoaderSupport {

    /**
     * The shared support instance, obtained via {@link ServiceLoader}.
     * <p>Preference order:
     * <ol>
     *   <li>First discovered <em>subclass</em> of {@code ServiceLoaderSupport}</li>
     *   <li>Otherwise the first discovered base implementation</li>
     *   <li>Otherwise a new base instance (final fallback)</li>
     * </ol>
     */
    public static final ServiceLoaderSupport INSTANCE = loadInstance();

    public ServiceLoaderSupport() { }

    /**
     * Finds a service instance of the given {@code type}.
     * <p>Lookup method: NetBeans {@code Lookup.getDefault().lookup(type)}.
     *
     * @param type the service interface or abstract class
     * @param <T>  the service type
     * @return an {@link Optional} containing the first discovered implementation,
     *         or {@link Optional#empty()} if none found
     * @throws NullPointerException if {@code type} is null
     */
    public <T> Optional<T> findService(Class<T> type) {
        return Optional.ofNullable(Lookup.getDefault().lookup(type));
    }

    // ---------------------------- internals ----------------------------

    private static ServiceLoaderSupport loadInstance() {
        List<ServiceLoaderSupport> providers = new ArrayList<>();
        for (ServiceLoaderSupport s : ServiceLoader.load(ServiceLoaderSupport.class))
            providers.add(s);
        if (providers.isEmpty())
            return new ServiceLoaderSupport();

        return getPreferredInstance(providers);
    }

    private static ServiceLoaderSupport getPreferredInstance(List<ServiceLoaderSupport> providers) {
        for (ServiceLoaderSupport s : providers)
            if (s.getClass() != ServiceLoaderSupport.class)
                return s;

        return providers.getFirst();
    }
}
