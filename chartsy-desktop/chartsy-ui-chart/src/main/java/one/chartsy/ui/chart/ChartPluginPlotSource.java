/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.util.List;

/** Explicit metadata for the plots rendered by a chart plugin. */
public interface ChartPluginPlotSource {

    List<PlotDescriptor> getPlotDescriptors();

    default String getPlotLabel(String plotId) {
        return getPlotDescriptors().stream()
                .filter(plot -> plot.id().equals(plotId))
                .map(PlotDescriptor::label)
                .findFirst()
                .orElse(plotId);
    }

    record PlotDescriptor(
            String id,
            String label,
            String colorParameterId,
            String strokeParameterId,
            String visibilityParameterId,
            String panelParameterId) {

        public PlotDescriptor {
            if (id == null || id.isBlank())
                throw new IllegalArgumentException("id is blank");
            if (label == null || label.isBlank())
                throw new IllegalArgumentException("label is blank");
            colorParameterId = colorParameterId == null ? "" : colorParameterId;
            strokeParameterId = strokeParameterId == null ? "" : strokeParameterId;
            if (visibilityParameterId == null || visibilityParameterId.isBlank())
                throw new IllegalArgumentException("visibilityParameterId is blank");
            if (panelParameterId == null || panelParameterId.isBlank())
                throw new IllegalArgumentException("panelParameterId is blank");
        }
    }
}
