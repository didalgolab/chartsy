/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.api.messages.handlers.ShutdownResponseHandler;
import one.chartsy.core.event.ListenerList;

public abstract class AbstractAlgorithmContext implements AlgorithmContext {
    private final ListenerList<ShutdownResponseHandler> shutdownResponseHandlers = ListenerList.of(ShutdownResponseHandler.class);
    private final String name;
    private volatile boolean shutdown;


    protected AbstractAlgorithmContext(String name) {
        this.name = name;
        addShutdownResponseHandler(__ -> this.shutdown = true);
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public void addShutdownResponseHandler(ShutdownResponseHandler handler) {
        shutdownResponseHandlers.addListener(handler);
    }

    @Override
    public void removeShutdownResponseHandler(ShutdownResponseHandler handler) {
        shutdownResponseHandlers.removeListener(handler);
    }

    @Override
    public final ShutdownResponseHandler getShutdownResponseHandler() {
        return shutdownResponseHandlers.fire();
    }

    @Override
    public final boolean isShutdown() {
        return shutdown;
    }
}
