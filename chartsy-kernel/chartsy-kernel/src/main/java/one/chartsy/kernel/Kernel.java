/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel;

import one.chartsy.kernel.config.KernelConfiguration;
import org.openide.util.Lookup;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

public class Kernel {

    private static final String[] EMPTY_ARGS = new String[0];

    private final ConfigurableApplicationContext current;

    public Kernel() {
        this(Lookup.getDefault().lookup(KernelConfiguration.class), EMPTY_ARGS);
    }

    public Kernel(KernelConfiguration configuration, String[] args) {
        StartupMetrics.mark("kernel:start");
        BufferingApplicationStartup startup = StartupMetrics.createSpringTimeline(4_096);
        current = configuration.createApplicationContext(startup, args);
        ((GenericApplicationContext) current)
                .registerBean("kernel", Kernel.class, () -> this);
        StartupMetrics.dumpSpringTimeline("kernel", startup);
        StartupMetrics.mark("kernel:ready");
    }

    public static Kernel getDefault() {
        return Lookup.getDefault().lookup(Kernel.class);
    }

    public ApplicationContext getApplicationContext() {
        return current;
    }

    public void publishEvent(Object event) {
        getApplicationContext().publishEvent(event);
    }
}
