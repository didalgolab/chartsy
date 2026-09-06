/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.action;

import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.StudyRegistry;
import org.junit.jupiter.api.Test;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

import javax.swing.Action;
import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

class ChartActionsTest {

    @Test
    void openIndicators_convenience_overload_preserves_legacy_provider_arguments() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var chart = new ChartFrame();
            var services = new RecordingActionServices();
            Lookups.executeWith(new ProxyLookup(Lookups.singleton(services), Lookup.getDefault()), () -> {
                assertThat(ChartActions.openIndicators(chart)).isInstanceOf(DefaultChartActionServices.IndicatorsOpen.class);
                assertThat(services.actionName).isEqualTo("IndicatorsOpen");
                assertThat(services.arguments).containsExactly(chart);
            });
        });
    }

    @Test
    void openIndicators_null_selection_preserves_legacy_provider_arguments() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var chart = new ChartFrame();
            var services = new RecordingActionServices();
            Lookups.executeWith(new ProxyLookup(Lookups.singleton(services), Lookup.getDefault()), () -> {
                assertThat(ChartActions.openIndicators(chart, null)).isInstanceOf(DefaultChartActionServices.IndicatorsOpen.class);
                assertThat(services.actionName).isEqualTo("IndicatorsOpen");
                assertThat(services.arguments).containsExactly(chart);
            });
        });
    }

    @Test
    void openIndicators_explicit_selection_passes_the_exact_study_to_the_provider() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var chart = new ChartFrame();
            var study = StudyRegistry.getDefault().getOverlay("FRAMA, Leading");
            var services = new RecordingActionServices();
            Lookups.executeWith(new ProxyLookup(Lookups.singleton(services), Lookup.getDefault()), () -> {
                assertThat(ChartActions.openIndicators(chart, study)).isInstanceOf(DefaultChartActionServices.IndicatorsOpen.class);
                assertThat(services.actionName).isEqualTo("IndicatorsOpen");
                assertThat(services.arguments).hasSize(2);
                assertThat(services.arguments[0]).isSameAs(chart);
                assertThat(services.arguments[1]).isSameAs(study);
            });
        });
    }

    private static final class RecordingActionServices implements ChartActionServices {
        private String actionName;
        private Object[] arguments;

        @Override
        public Action find(String name, Object... args) {
            actionName = name;
            arguments = args;
            return new DefaultChartActionServices().find(name, args);
        }

        @Override
        public void execute(String action, Object... args) {
            throw new AssertionError("Only action lookup is expected");
        }
    }
}
