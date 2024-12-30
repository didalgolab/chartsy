/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import lombok.Getter;
import one.chartsy.data.stream.Message;
import one.chartsy.data.stream.MessageChannel;
import one.chartsy.time.Clock;

@Getter
public class AlgorithmContextImpl extends AbstractAlgorithmContext {

    private final Clock clock;
    private final MessageChannel<Message> messageChannel;

    public AlgorithmContextImpl(String name, Clock clock, MessageChannel<Message> messageChannel) {
        super(name);
        this.clock = clock;
        this.messageChannel = messageChannel;
    }
}
