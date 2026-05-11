/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence;

import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import one.chartsy.Workspace;
import one.chartsy.kernel.StartupMetrics;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class SharedPersistenceBootstrap {

    private static final AtomicReference<JdbcDataSource> DATA_SOURCE = new AtomicReference<>();
    private static final AtomicReference<CompletableFuture<JdbcDataSource>> READY_FUTURE = new AtomicReference<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("chartsy-persistence-", 0).factory());

    private SharedPersistenceBootstrap() {
    }

    public static CompletableFuture<JdbcDataSource> startAsync() {
        return startAsync(EXECUTOR);
    }

    public static CompletableFuture<JdbcDataSource> startAsync(Executor executor) {
        for (; ; ) {
            var existing = READY_FUTURE.get();
            if (existing != null) {
                if (canReuse(existing))
                    return existing;
                READY_FUTURE.compareAndSet(existing, null);
                continue;
            }

            var created = new CompletableFuture<JdbcDataSource>();
            if (READY_FUTURE.compareAndSet(null, created)) {
                executor.execute(() -> completeBootstrap(created));
                return created;
            }
        }
    }

    public static DataSource awaitReady() {
        try {
            return startAsync(Runnable::run).join();
        } catch (CompletionException e) {
            throw unwrap(e);
        }
    }

    public static JdbcDataSource dataSource() {
        var existing = DATA_SOURCE.get();
        if (existing != null)
            return existing;

        var created = createDataSource();
        if (DATA_SOURCE.compareAndSet(null, created))
            return created;
        return DATA_SOURCE.get();
    }

    private static void completeBootstrap(CompletableFuture<JdbcDataSource> readyFuture) {
        try {
            readyFuture.complete(bootstrap());
        } catch (Throwable t) {
            READY_FUTURE.compareAndSet(readyFuture, null);
            readyFuture.completeExceptionally(t);
        }
    }

    private static JdbcDataSource bootstrap() {
        StartupMetrics.mark("persistenceBootstrap:start");
        var dataSource = dataSource();
        StartupMetrics.mark("persistenceBootstrap:dataSourceReady");

        StartupMetrics.mark("persistenceBootstrap:liquibase:start");
        try {
            var liquibase = new SpringLiquibase();
            liquibase.setDataSource(dataSource);
            liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
            liquibase.afterPropertiesSet();
        } catch (LiquibaseException e) {
            throw new IllegalStateException("Cannot initialize persistence schema", e);
        } finally {
            StartupMetrics.mark("persistenceBootstrap:liquibase:ready");
        }

        StartupMetrics.mark("persistenceBootstrap:ready");
        return dataSource;
    }

    private static boolean canReuse(CompletableFuture<JdbcDataSource> readyFuture) {
        if (!readyFuture.isDone())
            return true;
        if (readyFuture.isCancelled() || readyFuture.isCompletedExceptionally())
            return false;
        return isDatabaseFilePresent();
    }

    private static JdbcDataSource createDataSource() {
        try {
            Files.createDirectories(Workspace.current().path());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create workspace directory", e);
        }

        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:file:" + Workspace.current().path().resolve("application") + ";COMPRESS=true");
        dataSource.setUser("chartsy");
        dataSource.setPassword("chartsy");
        return dataSource;
    }

    private static boolean isDatabaseFilePresent() {
        var databasePath = databasePath();
        var parent = databasePath.getParent();
        var fileName = databasePath.getFileName().toString();
        return Files.exists(parent.resolve(fileName + ".mv.db"))
                || Files.exists(parent.resolve(fileName + ".h2.db"));
    }

    private static Path databasePath() {
        return Workspace.current().path().resolve("application");
    }

    private static RuntimeException unwrap(CompletionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException runtimeException)
            return runtimeException;
        return new IllegalStateException("Cannot initialize persistence schema", cause);
    }
}
