/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import org.openide.util.Lookup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * An immutable registry of chart plugins keyed by their display name.
 *
 * @param <T>
 *            the registered plugin type
 *
 * @author Mariusz Bernacki
 */
final class ChartPluginRegistry<T extends ChartPlugin<?>> {
    private final LinkedHashMap<String, T> pluginsByName;
    private final List<T> plugins;
    private final List<String> names;

    ChartPluginRegistry(Class<T> pluginType) {
        this(Lookup.getDefault().lookupAll(pluginType));
    }

    ChartPluginRegistry(Collection<? extends T> plugins) {
        Objects.requireNonNull(plugins, "plugins");

        TreeMap<String, T> orderedPlugins = new TreeMap<>();
        for (T plugin : plugins)
            if (plugin != null)
                orderedPlugins.put(Objects.requireNonNull(plugin.getName(), "plugin.name"), plugin);

        this.pluginsByName = new LinkedHashMap<>(orderedPlugins);
        this.plugins = List.copyOf(this.pluginsByName.values());
        this.names = List.copyOf(this.pluginsByName.keySet());
    }

    T get(String key) {
        return pluginsByName.get(key);
    }

    List<T> getPlugins() {
        return new ArrayList<>(plugins);
    }

    List<String> getNames() {
        return new ArrayList<>(names);
    }
}
