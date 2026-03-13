/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui;

import one.chartsy.kernel.Kernel;
import one.chartsy.kernel.boot.FrontEnd;
import one.chartsy.kernel.boot.SymbolsApplicationContextFactory;
import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import org.openide.util.Lookup;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StartupServices {

    private static final ExecutorService EXECUTOR = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("chartsy-startup-", 0).factory());
    private static volatile CompletableFuture<Kernel> kernelFuture;
    private static volatile CompletableFuture<FrontEnd> frontEndFuture;
    private static volatile CompletableFuture<ConfigurableApplicationContext> symbolsFuture;
    private static volatile boolean kernelSymbolBridgeInstalled;

    private StartupServices() {
    }

    public static void prewarm() {
        symbols();
    }

    public static void prewarmFullStack() {
        frontEnd();
    }

    public static CompletableFuture<ConfigurableApplicationContext> symbols() {
        CompletableFuture<ConfigurableApplicationContext> future = symbolsFuture;
        if (future == null) {
            synchronized (StartupServices.class) {
                future = symbolsFuture;
                if (future == null) {
                    future = CompletableFuture.supplyAsync(SymbolsApplicationContextFactory::createApplicationContext, EXECUTOR)
                            .thenApply(context -> {
                                bridgeKernelSymbolEvents(context);
                                return context;
                            });
                    symbolsFuture = future;
                }
            }
        }
        return future;
    }

    public static CompletableFuture<Kernel> kernel() {
        CompletableFuture<Kernel> future = kernelFuture;
        if (future == null) {
            synchronized (StartupServices.class) {
                future = kernelFuture;
                if (future == null) {
                    future = CompletableFuture.supplyAsync(Kernel::getDefault, EXECUTOR);
                    kernelFuture = future;
                }
            }
        }
        return future;
    }

    private static void bridgeKernelSymbolEvents(ConfigurableApplicationContext symbolsContext) {
        kernel().thenAcceptAsync(kernel -> {
            synchronized (StartupServices.class) {
                if (kernelSymbolBridgeInstalled)
                    return;
                kernelSymbolBridgeInstalled = true;
            }

            var eventMulticaster = kernel.getApplicationContext().getBean(
                    AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
                    ApplicationEventMulticaster.class);
            eventMulticaster.addApplicationListener(new ApplicationListener<PayloadApplicationEvent<SymbolGroupAggregateData>>() {
                @Override
                public void onApplicationEvent(PayloadApplicationEvent<SymbolGroupAggregateData> event) {
                    symbolsContext.publishEvent(event.getPayload());
                }
            });
        }, EXECUTOR);
    }

    public static CompletableFuture<FrontEnd> frontEnd() {
        CompletableFuture<FrontEnd> future = frontEndFuture;
        if (future == null) {
            synchronized (StartupServices.class) {
                future = frontEndFuture;
                if (future == null) {
                    future = kernel().thenApplyAsync(ignored -> Lookup.getDefault().lookup(FrontEnd.class), EXECUTOR);
                    frontEndFuture = future;
                }
            }
        }
        return future;
    }
}
