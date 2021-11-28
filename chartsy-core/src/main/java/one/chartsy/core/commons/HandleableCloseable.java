package one.chartsy.core.commons;

/**
 * A resource which can be closed and can have custom close handlers attached.
 * By design this class is intended to be used for resources not related with
 * I/O operations (for example, UI widgets) thus its {@link #close()} method
 * does not allow {@code IOException} or any checked exception to be thrown.
 * 
 * @author Mariusz Bernacki
 *
 * @param <T>
 *            the type of the resource handled
 */
public interface HandleableCloseable<T> extends AutoCloseable {
    
    /**
     * Adds a handler that will be called upon a resource close. The API defines no
     * guarantee whether the handler will be called before or after the close
     * actually takes place.
     * 
     * @param closeHandler
     *            the close handler to be notified of a resource close
     */
    void addCloseHandler(CloseHandler<T> closeHandler);
    
    /**
     * Removes the specified handler.
     * 
     * @param closeHandler
     *            the close handler to be removed
     */
    void removeCloseHandler(CloseHandler<T> closeHandler);
    
    /**
     * Closes the resource, notifying all registered close handlers and waiting for
     * handlers event processing to finish.
     */
    @Override void close();
}
