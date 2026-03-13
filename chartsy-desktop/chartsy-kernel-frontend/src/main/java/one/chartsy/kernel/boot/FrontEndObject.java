/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.boot;

import one.chartsy.kernel.Kernel;
import one.chartsy.kernel.StartupMetrics;
import one.chartsy.kernel.boot.config.FrontEndConfiguration;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

@ServiceProvider(service = FrontEnd.class)
public class FrontEndObject implements FrontEnd {

    private final SpringApplicationBuilder current;

    public FrontEndObject() {
        this(Kernel.getDefault());
    }

    public FrontEndObject(Kernel kernel) {
        this(Lookup.getDefault().lookup(FrontEndConfiguration.class), kernel);
    }

    public FrontEndObject(FrontEndConfiguration configuration, Kernel kernel) {
        StartupMetrics.mark("frontend:start");
        BufferingApplicationStartup startup = StartupMetrics.createSpringTimeline(4_096);
        current = configuration.createSpringApplicationBuilder();
        if (startup != null)
            current.application().setApplicationStartup(startup);
        var ctx = (GenericApplicationContext) current.run();
        ctx.registerBean("frontEnd", FrontEnd.class, () -> this);
        ctx.registerBean("kernel", Kernel.class, () -> kernel);
        StartupMetrics.dumpSpringTimeline("frontend", startup);
        StartupMetrics.mark("frontend:ready");
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return current.context();
    }
}
