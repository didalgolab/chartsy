/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import java.awt.Dimension;
import java.util.Optional;

public final class ExportOptions {

    public static final Dimension DEFAULT_DIMENSIONS = new Dimension(1536, 793);

    private final Optional<ChartExporter.ExportFormat<?>> format;
    private final Dimension dimensions;

    @lombok.Builder(builderClassName = "Builder")
    private ExportOptions(ChartExporter.ExportFormat<?> format, Dimension dimensions) {
        this.format = Optional.ofNullable(format);
        this.dimensions = (dimensions != null) ? new Dimension(dimensions) : new Dimension(DEFAULT_DIMENSIONS);
    }

    public static final ExportOptions DEFAULT = ExportOptions.builder().build();

    public Optional<ChartExporter.ExportFormat<?>> getFormat() {
        return format;
    }

    public Dimension getDimensions() {
        return new Dimension(dimensions);
    }
}
