/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.data;

import one.chartsy.SymbolResource;
import one.chartsy.data.Series;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.data.provider.DataProviders;
import one.chartsy.time.Chronological;

import java.util.concurrent.CompletableFuture;

public class SymbolResourceLoaderTask<E extends Chronological> extends CompletableFuture<Series<E>> implements Runnable {

    private final DataProvider provider;
    private final SymbolResource<E> resource;


    public SymbolResourceLoaderTask(DataProvider provider, SymbolResource<E> resource) {
        this.provider = provider;
        this.resource = resource;
    }

    @Override
    public void run() {
        try {
            DataProvider provider = this.provider;
            Series<E> series = DataProviders.getSeries(provider, (Class<E>)resource.dataType(), resource);

            // complete, if the task is not cancelled
            if (!isCancelled())
                complete(series);

        } catch (Exception | Error e) {
            completeExceptionally(e);
        }
    }
}
