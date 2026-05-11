/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui;

import one.chartsy.kernel.Kernel;
import one.chartsy.kernel.StartupMetrics;
import one.chartsy.kernel.boot.FrontEnd;
import one.chartsy.kernel.boot.SymbolsApplicationContextFactory;
import one.chartsy.persistence.domain.model.ChartTemplateRepository;
import one.chartsy.persistence.domain.model.RunnerRepository;
import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import one.chartsy.ui.chart.ChartTemplateCatalog;
import one.chartsy.ui.chart.ChartTemplateSummary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openide.util.Lookup;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class StartupServices {
    private static final Logger log = LogManager.getLogger(StartupServices.class);

    private static final ExecutorService EXECUTOR = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("chartsy-startup-", 0).factory());
    private static volatile CompletableFuture<Kernel> kernelFuture;
    private static volatile CompletableFuture<FrontEnd> frontEndFuture;
    private static volatile CompletableFuture<DataSource> persistenceFuture;
    private static volatile CompletableFuture<ConfigurableApplicationContext> symbolsFuture;
    private static volatile CompletableFuture<Kernel> chartTemplateWarmupFuture;
    private static volatile CompletableFuture<Kernel> runnerWarmupFuture;
    private static volatile CompletableFuture<ChartTemplateCatalog.LoadedTemplate> builtInTemplateFuture;
    private static volatile CompletableFuture<List<ChartTemplateSummary>> chartTemplatesFuture;
    private static volatile boolean kernelSymbolBridgeInstalled;

    private StartupServices() {
    }

    public static void prewarm() {
        symbols();
        kernel();
        builtInTemplate();
    }

    public static void prewarmFullStack() {
        frontEnd();
    }

    public static CompletableFuture<ConfigurableApplicationContext> symbols() {
        return getOrCreateFuture(
                () -> symbolsFuture,
                future -> symbolsFuture = future,
                StartupServices::createSymbolsFuture);
    }

    public static CompletableFuture<DataSource> persistence() {
        return getOrCreateFuture(
                () -> persistenceFuture,
                future -> persistenceFuture = future,
                StartupServices::createPersistenceFuture);
    }

    public static CompletableFuture<Kernel> warmChartTemplatePersistence() {
        return getOrCreateFuture(
                () -> chartTemplateWarmupFuture,
                future -> chartTemplateWarmupFuture = future,
                () -> createPersistenceWarmupFuture(
                        "kernelContext:chartTemplateWarmup",
                        StartupServices::prepareChartTemplatePersistence));
    }

    public static CompletableFuture<Kernel> warmRunnerRepository() {
        return getOrCreateFuture(
                () -> runnerWarmupFuture,
                future -> runnerWarmupFuture = future,
                () -> createPersistenceWarmupFuture(
                        "kernelContext:runnerWarmup",
                        StartupServices::prepareRunnerRepository));
    }

    public static CompletableFuture<Kernel> kernel() {
        return getOrCreateFuture(
                () -> kernelFuture,
                future -> kernelFuture = future,
                StartupServices::createKernelFuture);
    }

    private static void bridgeKernelSymbolEvents(ConfigurableApplicationContext symbolsContext) {
        kernel().thenAcceptAsync(kernel -> installKernelSymbolBridge(kernel, symbolsContext), EXECUTOR);
    }

    public static CompletableFuture<FrontEnd> frontEnd() {
        return getOrCreateFuture(
                () -> frontEndFuture,
                future -> frontEndFuture = future,
                StartupServices::createFrontEndFuture);
    }

    public static CompletableFuture<List<ChartTemplateSummary>> chartTemplates() {
        return getOrCreateFuture(
                () -> chartTemplatesFuture,
                future -> chartTemplatesFuture = future,
                StartupServices::createChartTemplatesFuture);
    }

    private static CompletableFuture<ChartTemplateCatalog.LoadedTemplate> builtInTemplate() {
        return getOrCreateFuture(
                () -> builtInTemplateFuture,
                future -> builtInTemplateFuture = future,
                StartupServices::createBuiltInTemplateFuture);
    }

    public static CompletableFuture<List<ChartTemplateSummary>> refreshChartTemplates() {
        synchronized (StartupServices.class) {
            chartTemplatesFuture = createChartTemplatesFuture();
            return chartTemplatesFuture;
        }
    }

    private static CompletableFuture<ConfigurableApplicationContext> createSymbolsFuture() {
        return CompletableFuture.supplyAsync(SymbolsApplicationContextFactory::createApplicationContext, EXECUTOR)
                .thenApply(context -> {
                    bridgeKernelSymbolEvents(context);
                    warmChartTemplatePersistence();
                    warmRunnerRepository();
                    chartTemplates();
                    return context;
                });
    }

    private static CompletableFuture<DataSource> createPersistenceFuture() {
        return SymbolsApplicationContextFactory.prewarmPersistence(EXECUTOR)
                .thenApply(DataSource.class::cast);
    }

    private static CompletableFuture<Kernel> createKernelFuture() {
        return CompletableFuture.supplyAsync(Kernel::getDefault, EXECUTOR);
    }

    private static CompletableFuture<Kernel> createPersistenceWarmupFuture(
            String stagePrefix,
            Consumer<ApplicationContext> warmupAction
    ) {
        return CompletableFuture.allOf(kernel(), persistence())
                .thenApplyAsync(ignored -> {
                    var kernel = kernel().join();
                    StartupMetrics.mark(stagePrefix + ":start");
                    try {
                        warmupAction.accept(kernel.getApplicationContext());
                        StartupMetrics.mark(stagePrefix + ":persistenceReady");
                        return kernel;
                    } finally {
                        StartupMetrics.mark(stagePrefix + ":ready");
                    }
                }, EXECUTOR);
    }

    private static void prepareChartTemplatePersistence(ApplicationContext applicationContext) {
        applicationContext.getBean(PlatformTransactionManager.class);
        applicationContext.getBean(ChartTemplateRepository.class);
    }

    private static void prepareRunnerRepository(ApplicationContext applicationContext) {
        applicationContext.getBean(RunnerRepository.class);
    }

    private static void installKernelSymbolBridge(Kernel kernel, ConfigurableApplicationContext symbolsContext) {
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
    }

    private static CompletableFuture<FrontEnd> createFrontEndFuture() {
        return kernel().thenApplyAsync(ignored -> Lookup.getDefault().lookup(FrontEnd.class), EXECUTOR);
    }

    private static CompletableFuture<ChartTemplateCatalog.LoadedTemplate> createBuiltInTemplateFuture() {
        return CompletableFuture.supplyAsync(() -> {
                    StartupMetrics.mark("chartTemplates:builtIn:start");
                    return ChartTemplateCatalog.builtInTemplate();
                }, EXECUTOR)
                .whenComplete((template, error) -> StartupMetrics.mark(
                        (error == null)
                                ? "chartTemplates:builtIn:ready"
                                : "chartTemplates:builtIn:failed"));
    }

    private static CompletableFuture<List<ChartTemplateSummary>> createChartTemplatesFuture() {
        var loadFuture = warmChartTemplatePersistence().thenApplyAsync(ignored -> {
            StartupMetrics.mark("chartTemplates:start");
            return ChartTemplateCatalog.getDefault().listTemplates();
        }, EXECUTOR);
        var handledFutureRef = new AtomicReference<CompletableFuture<List<ChartTemplateSummary>>>();
        var handledFuture = loadFuture.handle((templates, error) -> {
            if (error != null) {
                synchronized (StartupServices.class) {
                    if (chartTemplatesFuture == handledFutureRef.get())
                        chartTemplatesFuture = null;
                }
                StartupMetrics.mark("chartTemplates:failed");
                log.warn("Unable to prewarm chart templates", error);
                throw new CompletionException(error);
            }
            StartupMetrics.mark("chartTemplates:ready");
            return templates;
        });
        handledFutureRef.set(handledFuture);
        return handledFuture;
    }

    private static <T> CompletableFuture<T> getOrCreateFuture(
            Supplier<CompletableFuture<T>> futureReader,
            Consumer<CompletableFuture<T>> futureWriter,
            Supplier<CompletableFuture<T>> futureFactory
    ) {
        var future = futureReader.get();
        if (future != null)
            return future;

        synchronized (StartupServices.class) {
            future = futureReader.get();
            if (future == null) {
                future = futureFactory.get();
                futureWriter.accept(future);
            }
            return future;
        }
    }
}
