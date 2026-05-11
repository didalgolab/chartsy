/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.starter;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.metrics.ApplicationStartup;

public interface SpringApplicationBuilderFactory {

    default ConfigurableApplicationContext createApplicationContext(String... args) {
        return createApplicationContext(ApplicationStartup.DEFAULT, args);
    }

    ConfigurableApplicationContext createApplicationContext(ApplicationStartup applicationStartup, String... args);
}
