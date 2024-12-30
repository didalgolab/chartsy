/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.service;

import one.chartsy.data.stream.MessageBuffer;
import one.chartsy.data.stream.MessageHandler;

public class QueuedServiceWorker<T extends ManageableService> extends ServiceWorker<T> {

    protected final MessageBuffer queue;
    protected final MessageHandler handler;
    protected final int readLimit;

    public QueuedServiceWorker(T service, MessageBuffer queue, MessageHandler handler, int readLimit) {
        super(service);
        this.queue = queue;
        this.handler = handler;
        this.readLimit = readLimit;
    }

    @Override
    protected int doWorkUnit(int workDone) {
        return queue.read(handler, readLimit);
    }
}
