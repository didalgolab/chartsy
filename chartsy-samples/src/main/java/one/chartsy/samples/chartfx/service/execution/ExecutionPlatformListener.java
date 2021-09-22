package one.chartsy.samples.chartfx.service.execution;

import java.util.EventListener;

public interface ExecutionPlatformListener extends EventListener {
    /**
     * The order was filled
     *
     * @param event OrderEvent
     */
    void orderFilled(OrderEvent event);

    /**
     * The order was cancelled
     *
     * @param event OrderEvent
     */
    void orderCancelled(OrderEvent event);
}
