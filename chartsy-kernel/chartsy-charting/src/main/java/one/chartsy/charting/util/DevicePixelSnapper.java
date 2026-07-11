package one.chartsy.charting.util;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.geom.Rectangle2D;

/**
 * Converts axis-aligned Java2D user coordinates to and from the effective device-pixel grid.
 *
 * <p>The effective scale accounts for both the graphics transform and the device configuration,
 * which keeps offscreen scaled rendering and live HiDPI painting on the same pixel grid.</p>
 */
public final class DevicePixelSnapper {
    private final double scaleX;
    private final double scaleY;
    private final double translateX;
    private final double translateY;

    public DevicePixelSnapper(Graphics2D graphics) {
        var transform = graphics.getTransform();
        GraphicsConfiguration configuration = graphics.getDeviceConfiguration();
        double graphicsScaleX = 1.0;
        double graphicsScaleY = 1.0;
        if (configuration != null) {
            var defaultTransform = configuration.getDefaultTransform();
            graphicsScaleX = Math.abs(defaultTransform.getScaleX());
            graphicsScaleY = Math.abs(defaultTransform.getScaleY());
        }
        scaleX = normalizeScale(Math.max(Math.abs(transform.getScaleX()), graphicsScaleX));
        scaleY = normalizeScale(Math.max(Math.abs(transform.getScaleY()), graphicsScaleY));
        translateX = transform.getTranslateX();
        translateY = transform.getTranslateY();
    }

    public int snapX(double userX) {
        return (int) Math.round(userX * scaleX + translateX);
    }

    public int snapY(double userY) {
        return (int) Math.round(userY * scaleY + translateY);
    }

    public int interpolateX(double userStartX, double userEndX, double fraction) {
        int startX = snapX(userStartX);
        int endX = snapX(userEndX);
        return startX + (int) Math.round(fraction * (endX - startX));
    }

    private double toUserX(int deviceX) {
        return (deviceX - translateX) / scaleX;
    }

    private double toUserY(int deviceY) {
        return (deviceY - translateY) / scaleY;
    }

    public double deviceWidth(double userWidth) {
        return userWidth * scaleX;
    }

    public int deviceWidthRounded(double userWidth) {
        return Math.max(1, (int) Math.round(deviceWidth(userWidth)));
    }

    public int deviceHeightRounded(double userHeight) {
        return Math.max(1, (int) Math.round(userHeight * scaleY));
    }

    public Rectangle2D toUserRect(int deviceX, int deviceY, int deviceWidth, int deviceHeight) {
        return new Rectangle2D.Double(
                toUserX(deviceX),
                toUserY(deviceY),
                deviceWidth / scaleX,
                deviceHeight / scaleY);
    }

    private static double normalizeScale(double scale) {
        return Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
    }
}
