

/*
 * Licensed Materials - Property of Rogue Wave Software, Inc. 
 * © Copyright Rogue Wave Software, Inc. 2014, 2017 
 * © Copyright IBM Corp. 2009, 2014
 * © Copyright ILOG 1996, 2009
 * All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * The Software and Documentation were developed at private expense and
 * are "Commercial Items" as that term is defined at 48 CFR 2.101,
 * consisting of "Commercial Computer Software" and
 * "Commercial Computer Software Documentation", as such terms are
 * used in 48 CFR 12.212 or 48 CFR 227.7202-1 through 227.7202-4,
 * as applicable.
 */
package one.chartsy.charting.financial;

import one.chartsy.charting.Axis;
import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.DefaultStepsDefinition;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * This class defines a set of utility methods for the Stock demo.
 */
public class StockUtil {

  /** The minimal length of the visible x-range during zoom. */
  public static final double MIN_XLEN = 4;

  /** The default number of animation steps for zooming operations. */
  public static final int ANIMATION_STEPS = 8;

  /**
   * Performs an animated zoom.
   * 
   * @param chart
   *          The considered chart.
   * @param yAxisIdx
   *          The index of the considered y-axis.
   * @param w
   *          The new visible window.
   * @param zoomSteps
   *          The number of animation steps.
   */
  public static void performAnimatedZoom(Chart chart, int yAxisIdx, DataWindow w, int zoomSteps) {
    if (zoomSteps < 1) {
      chart.zoom(w, yAxisIdx);
      return;
    }
    Axis xAxis = chart.getXAxis();
    Axis yAxis = chart.getYAxis(yAxisIdx);
    DataInterval xRange = xAxis.getVisibleRange();
    DataInterval yRange = yAxis.getVisibleRange();
    DataInterval newXRange = w.xRange;
    DataInterval newYRange = w.yRange;
    chart.getChartArea().setDirectRedrawEnabled(true);
    xAxis.setAdjusting(true);
    yAxis.setAdjusting(true);
    int steps = zoomSteps + 1;
    double deltaXMin = (newXRange.min - xRange.min) / steps;
    double deltaXMax = (newXRange.max - xRange.max) / steps;
    double deltaYMin = (newYRange.min - yRange.min) / steps;
    double deltaYMax = (newYRange.max - yRange.max) / steps;
    final DataWindow dw = new DataWindow(xRange, yRange);
    for (int i = 0; i < steps; ++i) {
      dw.xRange.setMin(dw.getXMin() + deltaXMin);
      dw.xRange.setMax(dw.getXMax() + deltaXMax);
      dw.yRange.setMin(dw.getYMin() + deltaYMin);
      dw.yRange.setMax(dw.getYMax() + deltaYMax);
      chart.zoom(dw, yAxisIdx);
    }
    chart.getChartArea().setDirectRedrawEnabled(false);
    chart.zoom(w, yAxisIdx);
    xAxis.setAdjusting(false);
    yAxis.setAdjusting(false);
  }

  /**
   * Performs an animated zoom.
   * 
   * @param chart
   *          The considered chart.
   * @param yAxisIdx
   *          The index of the considered y-axis.
   * @param xRange
   *          The new visible range for the x-axis.
   * @param zoomSteps
   *          The number of animation steps.
   */
  public static void performAnimatedZoom(Chart chart, int yAxisIdx, DataInterval xRange, int zoomSteps) {
    DataWindow w = new DataWindow(xRange, getYDataRange(chart, yAxisIdx, xRange));
    performAnimatedZoom(chart, yAxisIdx, w, zoomSteps);
  }

  /**
   * Computes the y-range corresponding to the specified x-range. The returned
   * range is such that it contains all the data displayed within the specified
   * x-range.
   * 
   * @param chart
   *          The considered chart.
   * @param yAxisIdx
   *          The index of the considered y-axis.
   * @param xRange
   *          The visible range for the x-axis.
   * @return The y-range, such that it contains all the data displayed within
   *         the specified x-range.
   */
  public static DataInterval getYDataRange(Chart chart, int yAxisIdx, DataInterval xRange) {
    DataInterval res = new DataInterval();
    java.util.Iterator<ChartRenderer> iter = chart.getRendererIterator();
    DataInterval tmpRange = null;
    while (iter.hasNext()) {
      ChartRenderer r = iter.next();
      if (!r.isViewable() || r.getYAxisNumber() != yAxisIdx)
        continue;
      tmpRange = r.getYRange(xRange, tmpRange);
      res.add(tmpRange);
    }
    DefaultStepsDefinition.adjustRange(res);
    return res;
  }

  public static Icon loadIcon(String file, Class<?> baseClass) {
    Image image = loadImage(file, baseClass);
    return (image == null) ? null : new ImageIcon(image);
  }

  public static Icon loadIcon(String file) {
    return loadIcon(file, StockUtil.class);
  }

  public static Image loadImage(String file, Class<?> baseClass) {
    Image image = null;
    try {
      image = getImageFromFile(baseClass, file);
    } catch (Exception x) {
      System.err.println("Error while loading image: " + x.getMessage());
      x.printStackTrace();
    }
    return image;
  }

  public static Image loadImage(String file) {
    return loadImage(file, StockUtil.class);
  }

    /**
     * Loads an image from a classpath resource relative to the given base class,
     * or from the filesystem if not found on the classpath. Returns a fully
     * realized Image. Prefers ImageIO when possible, falling back to ImageIcon.
     *
     * @param baseClass The base class used to resolve relative resources. May be null.
     * @param file Resource path relative to baseClass package, an absolute resource (starting with "/"),
     *             or a filesystem path.
     * @return The loaded Image.
     * @throws IOException If the image cannot be found or read.
     */
    public static Image getImageFromFile(Class<?> baseClass, String file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file must not be null or empty");
        }
        Class<?> cls = (baseClass != null ? baseClass : StockUtil.class);

        // 1) Try classpath via baseClass (relative or absolute)
        URL url = cls.getResource(file);
        if (url == null && file.startsWith("/")) {
            // Some launchers may require stripping the leading slash for class loader access
            url = cls.getResource(file.substring(1));
        }
        if (url == null) {
            ClassLoader cl = cls.getClassLoader();
            if (cl != null) {
                String name = file.startsWith("/") ? file.substring(1) : file;
                url = cl.getResource(name);
            }
        }
        if (url != null) {
            // Try ImageIO first for a BufferedImage, then fall back to ImageIcon
            BufferedImage bi = ImageIO.read(url);
            if (bi != null) {
                return bi;
            }
            return new ImageIcon(url).getImage();
        }

        // 2) Try filesystem
        Path path = Path.of(file);
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                BufferedImage bi = ImageIO.read(in);
                if (bi != null) {
                    return bi;
                }
            }
            return new ImageIcon(file).getImage();
        }

        throw new IOException("Image not found: " + file);
    }

  private StockUtil() {
  }

}

