/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.boot;

import one.chartsy.kernel.Kernel;
import one.chartsy.kernel.boot.config.FrontEndConfiguration;
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
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
        current = configuration.createSpringApplicationBuilder();
        // FIXME: why do I need to do this? (fails in maven build without it)
        TomcatURLStreamHandlerFactory.disable();
        var ctx = (GenericApplicationContext) current.run();
        ctx.registerBean("frontEnd", FrontEnd.class, () -> this);
        ctx.registerBean("kernel", Kernel.class, () -> kernel);
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return current.context();
    }
}
