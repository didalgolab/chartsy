/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core;

import java.util.List;
import java.util.Objects;

public class LaunchConfiguration<R> {
    private final List<Class<? extends LaunchableTarget<R>>> targets;

    public static <R> LaunchConfiguration<R> of(Class<? extends LaunchableTarget<R>> targetType) {
        return new LaunchConfiguration<>(List.of(targetType));
    }

    private LaunchConfiguration(List<Class<? extends LaunchableTarget<R>>> targets) {
        this.targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
    }

    public final List<Class<? extends LaunchableTarget<R>>> getTargets() {
        return targets;
    }
}
