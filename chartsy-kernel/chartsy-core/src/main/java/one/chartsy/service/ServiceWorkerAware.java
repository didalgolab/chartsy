/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.service;

import one.chartsy.util.ExceptionHandler;

/**
 * An interface that extends {@link ExceptionHandler} and provides additional
 * hooks for a service worker's lifecycle. This interface defines default behaviors
 * for handling work done by the service worker, as well as signals for closing
 * the service worker.
 */
public interface ServiceWorkerAware extends ExceptionHandler {

    /**
     * A no-operation implementation of {@link ServiceWorkerAware} that provides
     * default, no-op behavior for all methods.
     */
    ServiceWorkerAware NO_OP = new ServiceWorkerAware() {};

    /**
     * Hook method that is called before the main work is done by the service worker.
     * The default implementation does nothing and returns 0.
     *
     * @param workDone an integer representing the amount of work done so far
     * @return an integer representing the adjusted amount of work done
     */
    default int doFirst(int workDone) {
        return 0;
    }

    /**
     * Hook method that is called after the main work is done by the service worker.
     * The default implementation does nothing and returns 0.
     *
     * @param workDone an integer representing the amount of work done
     * @return an integer representing the adjusted amount of work done
     */
    default int doLast(int workDone) {
        return 0;
    }

    /**
     * Hook method that is called when a close signal is received by the service worker.
     * The default implementation does nothing.
     */
    default void onCloseSignal() {
    }

    /**
     * Determines whether the service worker wants to close. The default implementation
     * always returns {@code false}.
     *
     * @return {@code true} if the service worker wants to close, {@code false} otherwise
     */
    default boolean wantsToClose() {
        return false;
    }

    /**
     * Determines whether the service worker is ready to close. The default implementation
     * always returns {@code true}.
     *
     * @return {@code true} if the service worker is ready to close, {@code false} otherwise
     */
    default boolean readyToClose() {
        return true;
    }

    /**
     * Handles an exception by rethrowing it as an unchecked exception. This method
     * overrides the {@link ExceptionHandler#onException(Throwable)} method and
     * provides a default implementation that rethrows the exception.
     *
     * @param e the exception to handle
     */
    @Override
    default void onException(Throwable e) {
        throw rethrowAsUnchecked(e);
    }

    /**
     * Rethrows the given throwable as an unchecked exception.
     *
     * @param t the throwable to rethrow
     * @throws T the {@code t} rethrown
     */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T rethrowAsUnchecked(Throwable t) throws T {
        throw (T) t;
    }

    /**
     * Returns the given object as a {@code ServiceWorkerAware} instance if it is
     * an instance of {@code ServiceWorkerAware} and non-null, otherwise returns
     * {@link #NO_OP}. This method provides a convenient way to handle potentially
     * null objects that might be {@code ServiceWorkerAware} instances.
     *
     * @param maybeWorkerAware the object to check
     * @return the object as a {@code ServiceWorkerAware} if possible, otherwise {@link #NO_OP}
     */
    static ServiceWorkerAware fromNullable(Object maybeWorkerAware) {
        return (maybeWorkerAware instanceof ServiceWorkerAware workerAware) ? workerAware : NO_OP;
    }
}