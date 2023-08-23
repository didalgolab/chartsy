/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core;

@FunctionalInterface
public interface NamedPluginQuery<R, T extends NamedPlugin<T>> {

    R queryFrom(NamedPlugin<? extends T> plugin);
}
