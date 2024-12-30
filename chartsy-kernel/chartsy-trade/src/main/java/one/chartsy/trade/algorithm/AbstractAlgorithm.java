/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.api.messages.ShutdownRequest;
import one.chartsy.time.Clock;

public abstract class AbstractAlgorithm implements Algorithm {

    protected final AlgorithmContext context;
    protected final String name;

    public AbstractAlgorithm(AlgorithmContext context) {
        this.context = context;
        this.name = context.getName();
    }

    public final String name() {
        return name;
    }

    public final Clock clock() {
        return context.getClock();
    }

    @Override
    public void open() {
        // nothing to do here
    }

    @Override
    public void close() {
        // nothing to do here
    }

    @Override
    public void onShutdownRequest(ShutdownRequest request) {
        context.getShutdownResponseHandler().onShutdownResponse(request.toShutdownResponse(name()));
    }
}
