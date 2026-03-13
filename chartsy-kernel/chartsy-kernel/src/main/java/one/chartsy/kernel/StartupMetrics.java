/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel;

import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.metrics.buffering.StartupTimeline;
import org.springframework.core.metrics.StartupStep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class StartupMetrics {

    private static final long JVM_START_NANO_TIME = System.nanoTime();
    private static final String LOG_PATH = System.getProperty("chartsy.startup.log");
    private static final long LAUNCH_EPOCH_MILLIS = Long.getLong("chartsy.startup.launchEpochMs", -1L);
    private static final boolean SPRING_TIMELINE_ENABLED = Boolean.getBoolean("chartsy.startup.springTimeline");
    private static final Object LOCK = new Object();

    private StartupMetrics() {
    }

    public static boolean isEnabled() {
        return LOG_PATH != null && !LOG_PATH.isBlank();
    }

    public static void mark(String stage) {
        if (!isEnabled()) {
            return;
        }

        long nowEpochMillis = System.currentTimeMillis();
        long jvmElapsedMillis = (System.nanoTime() - JVM_START_NANO_TIME) / 1_000_000L;
        long launchElapsedMillis = (LAUNCH_EPOCH_MILLIS >= 0L) ? nowEpochMillis - LAUNCH_EPOCH_MILLIS : -1L;
        append("%s | launch=%dms | jvm=%dms | thread=%s | %s%n".formatted(
                Instant.ofEpochMilli(nowEpochMillis),
                launchElapsedMillis,
                jvmElapsedMillis,
                Thread.currentThread().getName(),
                stage));
    }

    public static BufferingApplicationStartup createSpringTimeline(int capacity) {
        return SPRING_TIMELINE_ENABLED ? new BufferingApplicationStartup(capacity) : null;
    }

    public static void dumpSpringTimeline(String scope, BufferingApplicationStartup startup) {
        if (!isEnabled() || startup == null) {
            return;
        }

        StartupTimeline timeline = startup.drainBufferedTimeline();
        var events = timeline.getEvents().stream()
                .sorted(Comparator.comparing(StartupTimeline.TimelineEvent::getDuration).reversed())
                .limit(30)
                .toList();
        for (var event : events) {
            StartupStep step = event.getStartupStep();
            String tags = StreamSupport.stream(step.getTags().spliterator(), false)
                    .map(tag -> tag.getKey() + "=" + tag.getValue())
                    .collect(Collectors.joining(","));
            append("spring | scope=%s | duration=%dms | step=%s | tags=%s%n".formatted(
                    scope,
                    event.getDuration().toMillis(),
                    step.getName(),
                    tags));
        }
    }

    private static void append(String line) {
        synchronized (LOCK) {
            try {
                Path path = Path.of(LOG_PATH);
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(path, List.of(line), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // Keep startup diagnostics best-effort only.
            }
        }
    }
}
