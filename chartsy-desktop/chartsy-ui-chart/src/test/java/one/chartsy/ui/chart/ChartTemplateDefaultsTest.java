/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChartTemplateDefaultsTest {

    @Test
    void basicChartTemplateContainsOnlyResolvedStudies() {
        ChartTemplate template = ChartTemplateDefaults.basicChartTemplate();

        assertThat(template.getChart()).isNotNull();
        assertThat(template.getOverlays()).doesNotContainNull();
        assertThat(template.getIndicators()).doesNotContainNull();
        assertThat(template.getOverlays())
                .extracting(Overlay::getName)
                .contains("FRAMA, Leading", "FRAMA, Trailing", "Sfora", "Sentiment Bands");
        assertThat(template.getIndicators())
                .extracting(Indicator::getName)
                .containsExactly("Fractal Dimension");
    }
}
