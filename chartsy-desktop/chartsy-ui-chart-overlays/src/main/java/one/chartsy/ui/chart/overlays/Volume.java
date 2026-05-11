/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.overlays;

import java.awt.Color;
import java.awt.Rectangle;
import java.text.DecimalFormat;

import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.data.VisibleValues;
import one.chartsy.ui.chart.plot.AbstractTimeSeriesPlot;
import org.openide.util.lookup.ServiceProvider;

/**
 * The volume overlay by default plotted as a semi-transparent histogram bars at
 * the bottom part of the price chart.
 * 
 * @author Mariusz Bernacki
 *
 */
@ServiceProvider(service = Overlay.class)
public class Volume extends AbstractOverlay {

    public static final String VOLUME = "volume";
    public static final String SMA = "sma";

    public Volume() {
        super("Volume");
    }
    
    @Override
    public String getName() {
        return "Volume";
    }
    
    @Override
    public String getLabel() {
        return "Volume";
    }
    
    public String getPaintedLabel(ChartFrame cf) {
        DecimalFormat df = new DecimalFormat("###,###");
        String factor = df.format((int) getVolumeFactor(cf));
        return getLabel() + " x " + factor;
    }
    
    @Override
    public Range getRange(ChartContext cf) {
        VisibleValues values = visibleDataset(cf, VOLUME);
        Range.Builder builder = new Range.Builder().add(0.0);
        if (values != null)
            values.getRange(builder);

        Range range = builder.toRange();
        if (range.isEmpty())
            return Range.of(0.0, 1.0);
        if (range.length() == 0.0)
            return Range.of(0.0, Math.max(range.max(), 1.0));

        double margin = range.length() * 0.01;
        return Range.of(range.min() - margin, range.max() + margin);
    }
    
    @Override
    public void calculate() {
        CandleSeries initial = getDataset();
        if (initial != null) {
            DoubleSeries volume = initial.volumes();
            Range range = Range.of(0, max(volume));
            double factor = Math.pow(10, String.valueOf(Math.round(range.max())).length() - 1);
            volume = volume.div(factor);
            addPlot(VOLUME, new VolumeBarsPlot(volume.values(), color, widthPercent));
            //addPlot(SMA, new EmptyPlot(volume.sma(properties.getSmaPeriod()), properties.getColor()));
        }
    }

    private static double max(DoubleSeries values) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = values.length() - 1; i >= 0; i--)
            max = Math.max(max, values.get(i));

        return max;
    }

    @Parameter(name = "Color")
    public Color color = new Color(0xFABC7F);
    @Parameter(name = "SMA Color")
    public Color smaColor = Color.BLUE;
    @Parameter(name = "Transparency")
    public int transparency = 128;
    @Parameter(name = "Width (%)")
    public double widthPercent = 55.0;

    @Override
    public Color[] getColors() {
        return new Color[] { color, smaColor };
    }
    
    @Override
    public boolean getMarkerVisibility() {
        return false;
    }
    
    private double getVolumeFactor(ChartFrame cf) {
        VisibleValues values = visibleDataset(cf, VOLUME);
        if (values == null || values.getLength() == 0)
            return 1.0;
        double max = values.getMaximum();
        if (!Double.isFinite(max) || max <= 0.0)
            return 1.0;
        return Math.pow(10, String.valueOf(Math.round(max)).length() - 1);
    }
    
    @Override
    public boolean isIncludedInRange() {
        return false;
    }

    private static final class VolumeBarsPlot extends AbstractTimeSeriesPlot {
        private final double widthPercent;

        private VolumeBarsPlot(one.chartsy.base.DoubleDataset values, Color primaryColor, double widthPercent) {
            super(values, primaryColor);
            this.widthPercent = widthPercent;
        }

        @Override
        public void render(PlotRenderTarget target, PlotRenderContext context) {
            target.addDecoration((graphics, renderContext, coordinateSystem) -> {
                VisibleValues values = getVisibleData(renderContext.chartContext());
                if (values == null || values.getLength() == 0)
                    return;

                Rectangle plotBounds = coordinateSystem.plotBounds();
                if (plotBounds == null || plotBounds.isEmpty())
                    return;

                Range range = values.getRange(new Range.Builder().add(0.0)).toRange();
                double max = Math.max(1.0, range.max());
                double bottom = plotBounds.getMaxY();
                double bandHeight = plotBounds.height * 0.24;
                double baseY = bottom - 1.0;
                int barWidth = resolveBarWidth(renderContext.chartContext().getChartProperties());

                graphics.setColor(getPrimaryColor());
                for (int index = 0; index < values.getLength(); index++) {
                    double value = values.getValueAt(index);
                    if (!Double.isFinite(value))
                        continue;

                    double slot = renderContext.chartContext().getChartData().getVisibleStartSlot() + index;
                    double centerX = coordinateSystem.toDisplay(slot, 0.0).x;
                    double height = (value / max) * bandHeight;
                    int left = (int) Math.round(centerX - barWidth / 2.0);
                    int top = (int) Math.round(baseY - height);
                    int barHeight = Math.max(1, (int) Math.round(height));
                    graphics.fillRect(left, top, barWidth, barHeight);
                }
            }, context);
            if (context.legended())
                target.addLegendEntry(context.legendName(), LegendMarkerSpec.bar(getPrimaryColor()));
        }

        private int resolveBarWidth(ChartProperties chartProperties) {
            int candleBodyWidth = PixelPerfectCandleGeometry.snapBodyWidth(chartProperties.getBarWidth());
            double clampedWidthPercent = Math.clamp(widthPercent, 1.0, 100.0);
            int resolvedWidth = Math.max(1, (int) Math.round(candleBodyWidth * clampedWidthPercent / 100.0));
            if (clampedWidthPercent < 100.0 && candleBodyWidth > 1 && resolvedWidth >= candleBodyWidth)
                resolvedWidth = candleBodyWidth - 1;
            return resolvedWidth;
        }

        @Override
        public Range.Builder contributeRange(Range.Builder range, ChartContext cf) {
            return range == null ? new Range.Builder() : range;
        }
    }
}
