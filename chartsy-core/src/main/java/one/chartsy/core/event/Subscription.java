package one.chartsy.core.event;

import java.util.List;

/**
 * Subscription is a token for referring to added listeners so they can
 * be {@link Unsubscriber#unsubscribe unsubscribed}.
 */
public final class Subscription {
    
    private final List<?> list;
    private final Object listener;
    
    Subscription(List<?> list, Object listener) {
        this.list = list;
        this.listener = listener;
    }
    
    public interface Unsubscriber {
        default void unsubscribe(Subscription token) {
            token.list.remove(token.listener);
        }
    }
}