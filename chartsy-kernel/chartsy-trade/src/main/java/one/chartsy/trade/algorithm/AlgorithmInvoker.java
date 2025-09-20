/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import lombok.Getter;
import one.chartsy.base.OpenDisposable;
import one.chartsy.core.event.AbstractInvoker;
import one.chartsy.data.stream.Message;
import one.chartsy.data.stream.MessageHandler;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.messaging.common.ShutdownRequest;
import one.chartsy.messaging.common.ShutdownResponse;
import one.chartsy.messaging.common.handlers.ShutdownRequestHandler;
import one.chartsy.messaging.common.handlers.ShutdownResponseHandler;

@Getter
public class AlgorithmInvoker extends AbstractInvoker implements MessageHandler {

    private final OpenDisposable manageableHandler = getHandler(OpenDisposable.class);
    private final MarketMessageHandler marketMessageHandler = getHandler(MarketMessageHandler.class);

    public AlgorithmInvoker(Algorithm algorithm) {
        super(algorithm.getClass());
    }

    @Override
    public void handleMessage(Message message) {
        switch (message) {
            case ShutdownRequest sr -> getHandler(ShutdownRequestHandler.class).onShutdownRequest(sr);
            case ShutdownResponse sr -> getHandler(ShutdownResponseHandler.class).onShutdownResponse(sr);
            default -> { }
        }
    }
}
