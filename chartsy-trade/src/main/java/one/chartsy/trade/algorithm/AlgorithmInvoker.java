/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import lombok.Getter;
import one.chartsy.Manageable;
import one.chartsy.core.event.AbstractInvoker;
import one.chartsy.data.stream.Message;
import one.chartsy.data.stream.MessageHandler;
import one.chartsy.messaging.MarketMessageHandler;

@Getter
public class AlgorithmInvoker extends AbstractInvoker implements MessageHandler {

    private final Manageable manageableHandler = getHandler(Manageable.class);
    private final MarketMessageHandler marketMessageHandler = getHandler(MarketMessageHandler.class);

    public AlgorithmInvoker(Algorithm algorithm) {
        super(algorithm.getClass());
    }

    @Override
    public void handleMessage(Message msg) {
        var msgType = msg.type();
        var handler = getHandler(msgType.handlerType());
        if (handler != null) {
            msgType.handlerFunction().accept(handler, msg);
        }
    }
}
