/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartPluginRegistryTest {

    @Test
    void sortsPluginsByNameAndSupportsLookup() {
        DummyOverlay alpha = new DummyOverlay("Alpha");
        DummyOverlay beta = new DummyOverlay("Beta");
        DummyOverlay zeta = new DummyOverlay("Zeta");

        ChartPluginRegistry<DummyOverlay> registry = new ChartPluginRegistry<>(List.of(zeta, alpha, beta));

        assertThat(registry.getNames()).containsExactly("Alpha", "Beta", "Zeta");
        assertThat(registry.getPlugins()).extracting(Overlay::getName).containsExactly("Alpha", "Beta", "Zeta");
        assertThat(registry.get("Beta")).isSameAs(beta);
    }

    @Test
    void returnsDefensiveCopiesForNamesAndPlugins() {
        DummyOverlay alpha = new DummyOverlay("Alpha");
        DummyOverlay beta = new DummyOverlay("Beta");
        ChartPluginRegistry<DummyOverlay> registry = new ChartPluginRegistry<>(List.of(alpha, beta));

        List<String> names = registry.getNames();
        List<DummyOverlay> plugins = registry.getPlugins();

        names.clear();
        plugins.clear();

        assertThat(registry.getNames()).containsExactly("Alpha", "Beta");
        assertThat(registry.getPlugins()).containsExactly(alpha, beta);
    }

    @Test
    void laterPluginReplacesEarlierPluginWhenNamesCollide() {
        DummyOverlay first = new DummyOverlay("Shared");
        DummyOverlay second = new DummyOverlay("Shared");

        ChartPluginRegistry<DummyOverlay> registry = new ChartPluginRegistry<>(List.of(first, second));

        assertThat(registry.get("Shared")).isSameAs(second);
        assertThat(registry.getPlugins()).containsExactly(second);
    }

    private static final class DummyOverlay extends Overlay {
        private DummyOverlay(String name) {
            super(name);
        }

        @Override
        public String getLabel() {
            return getName();
        }

        @Override
        public DummyOverlay newInstance() {
            return new DummyOverlay(getName());
        }

        @Override
        public void calculate() {
            // no-op
        }

        @Override
        public boolean getMarkerVisibility() {
            return true;
        }
    }
}
