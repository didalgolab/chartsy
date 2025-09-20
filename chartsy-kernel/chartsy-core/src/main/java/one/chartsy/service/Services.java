/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.service;

import one.chartsy.service.loader.ServiceLoaderSupport;
import one.chartsy.service.loader.ServiceNotFoundException;

import java.util.Optional;

/**
 * Convenience façade for retrieving shared service instances.
 * <p>
 * Delegates to {@link ServiceLoaderSupport#INSTANCE}.
 */
public final class Services {

    private Services() {}

    /**
     * Returns a shared instance of the requested service type.
     *
     * @param type the service interface or abstract class
     * @param <T> the service type
     * @return the first discovered implementation instance
     * @throws NullPointerException if {@code type} is null
     * @throws IllegalStateException if no implementation can be found
     */
    public static <T> T getShared(Class<T> type) {
        return findShared(type).orElseThrow(() -> new ServiceNotFoundException(type.getName()));
    }

    /**
     * Gives an {@link Optional} describing a shared instance of the requested service type.
     *
     * @param type the service interface or abstract class
     * @param <T> the service type
     * @return an {@code Optional} with the first discovered implementation instance,
     *         or an empty {@code Optional} if none found
     * @throws NullPointerException if {@code type} is null
     */
    public static <T> Optional<T> findShared(Class<T> type) {
        return ServiceLoaderSupport.INSTANCE.findService(type);
    }
}
