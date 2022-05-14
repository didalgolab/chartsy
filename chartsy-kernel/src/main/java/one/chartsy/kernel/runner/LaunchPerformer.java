/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.runner;

import one.chartsy.Attribute;

import java.util.Collection;
import java.util.List;

@FunctionalInterface
public interface LaunchPerformer {

    void performLaunch(LaunchContext context, Class<?> target) throws Exception;

    default Collection<Attribute<?>> getRequiredConfigurations() {
        return List.of();
    }

    interface Descriptor {
        String getName();
        String getType();
        String getTopComponent();
    }
}
