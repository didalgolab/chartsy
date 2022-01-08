/* Copyright 2022 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.overlays;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.text.DecimalFormat;

import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.data.VisibleValues;
import one.chartsy.ui.chart.internal.ColorServices;
import one.chartsy.ui.chart.internal.Graphics2DHelper;
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
    public void paint(Graphics2D g, ChartContext cf, Rectangle bounds) {
        int boundsX = bounds.x, boundsWidth = bounds.width;
        bounds = cf.getMainPanel().getChartPanel().getBounds();
        bounds.x = boundsX;
        bounds.width = boundsWidth;
        VisibleValues d = visibleDataset(cf, VOLUME);
        VisibleValues sma = visibleDataset(cf, SMA);
        if (d != null) {
            int height = bounds.height / 4;
            Range range = getRange(cf);
            Rectangle rect = new Rectangle(bounds.x, bounds.y + bounds.height - height, bounds.width, height);
            ColorServices colorServices = ColorServices.getDefault();
            Color colorVolume = colorServices.getTransparentColor(color, transparency);
            Graphics2DHelper.bar(g, cf, range, rect, d, colorVolume);
            
            if (sma != null) {
                //Color colorSma = colorServices.getTransparentColor(properties.getSmaColor(), properties.getAlpha());
                //Graphics2DHelper.line(g, cf, range, rect, sma, colorSma, properties.getSmaStroke()); // paint
            }
        }
    }
    
    @Override
    public void calculate() {
        CandleSeries initial = getDataset();
        if (initial != null) {
            DoubleSeries volume = initial.volumes();
            Range range = Range.of(0, max(volume));
            double factor = Math.pow(10, String.valueOf(Math.round(range.getMax())).length() - 1);
            volume = volume.div(factor);
            addPlot(VOLUME, new EmptyPlot(volume.values(), color));
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
    public Color color = new Color(0xf57900);
    @Parameter(name = "SMA Color")
    public Color smaColor = Color.BLUE;
    @Parameter(name = "Transparency")
    public int transparency = 128;

    @Override
    public Color[] getColors() {
        return new Color[] { color, smaColor };
    }
    
    @Override
    public boolean getMarkerVisibility() {
        return false;
    }
    
    private double getVolumeFactor(ChartFrame cf) {
        return Math.pow(10, String.valueOf(Math.round(cf.getChartData().getVisible().getVolumes().getMaximum())).length() - 1);
    }
    
    @Override
    public boolean isIncludedInRange() {
        return false;
    }
}
