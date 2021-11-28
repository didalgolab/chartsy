package one.chartsy.core.commons;

/**
 * A handler which is notified when a resource is closed.
 * 
 * @author Mariusz Bernacki
 *
 * @param <T>
 *            the type of the resource
 * @see HandleableCloseable
 */
@FunctionalInterface
public interface CloseHandler<T> {
    
    /**
     * Receives a notification that the resource was closed.
     * 
     * @param closed
     *            the closed resource
     */
    void handleClose(T closed);
}
