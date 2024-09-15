/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.data.stream.MessageBuffer;
import one.chartsy.service.QueuedServiceWorker;

public class AlgorithmWorker extends QueuedServiceWorker<Algorithm> {

    private final Algorithm algorithm;
    private final AlgorithmInvoker invoker;
    private final MessageBuffer queue;
    private final MarketSupplier marketSupplier;

    public AlgorithmWorker(Algorithm algorithm, MessageBuffer queue, MarketSupplier marketSupplier) {
        super(algorithm, queue, new AlgorithmInvoker(algorithm), 1024);
        this.algorithm = algorithm;
        this.queue = queue;
        this.marketSupplier = marketSupplier;
        this.invoker = (AlgorithmInvoker) handler;
        this.invoker.addService(algorithm);
    }

    @Override
    public void onOpen() {
        marketSupplier.open();
        invoker.getManageableHandler().open();
        super.onOpen();
    }

    @Override
    public void onClose() {
        //super.onClose(); // already handled by ManageableHandler::close below
        marketSupplier.close();
        invoker.getManageableHandler().close();
    }

    @Override
    public int doWorkUnit(int workDone) {
        int work = marketSupplier.poll(invoker.getMarketMessageHandler(), 1024);

        work += super.doWorkUnit(workDone);
        return work;
    }
}
