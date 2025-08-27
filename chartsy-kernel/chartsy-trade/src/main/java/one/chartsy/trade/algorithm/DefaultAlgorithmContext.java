/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import lombok.Getter;
import one.chartsy.data.stream.Message;
import one.chartsy.data.stream.MessageChannel;
import one.chartsy.time.Clock;
import one.chartsy.trade.service.OrderHandler;
import one.chartsy.util.SequenceGenerator;

/**
 * A standard implementation of the AlgorithmContext.
 */
@Getter
public class DefaultAlgorithmContext extends AbstractAlgorithmContext {

    private final Clock clock;
    private final MessageChannel<Message> messageChannel;
    private final OrderHandler orderHandler;
    private final SequenceGenerator sequenceGenerator;

    public DefaultAlgorithmContext(String id, Clock clock, MessageChannel<Message> messageChannel) {
        this(id, clock, messageChannel, null, null);
    }

    public DefaultAlgorithmContext(String id, Clock clock, MessageChannel<Message> messageChannel, OrderHandler orderHandler, SequenceGenerator sequenceGenerator) {
        super(id);
        this.clock = clock;
        this.messageChannel = messageChannel;
        this.orderHandler = orderHandler;
        this.sequenceGenerator = sequenceGenerator;
    }
}
