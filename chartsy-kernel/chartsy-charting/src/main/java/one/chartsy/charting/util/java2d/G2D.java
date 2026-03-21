package one.chartsy.charting.util.java2d;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

/// Collects Java2D helpers that cooperate with {@link AdaptablePaint}.
///
/// `Chart`, `PlotStyle`, and legend-marker rendering paths use these wrappers when the current
/// paint may adapt itself to the target bounds. Fill operations temporarily publish those bounds
/// through {@link AdaptablePaint#KEY_USER_BOUNDS}, while rectangular outline operations
/// temporarily disable antialiasing so adaptive paints rasterize on the same integer pixel grid
/// as the corresponding AWT rectangle primitives.
public class G2D {

    /// Rendering-hint key used to publish user-space bounds to adaptive paints.
    ///
    /// Only `Rectangle2D` instances and `null` are accepted, which lets callers install the
    /// current bounds before painting and reliably clear the hint afterward.
    static final class UserBoundsKey extends RenderingHints.Key {
        static final G2D.UserBoundsKey INSTANCE = new G2D.UserBoundsKey(20110426);

        private UserBoundsKey(int key) {
            super(key);
        }

        /// Returns whether `value` is interpolateColor supported bounds hint payload.
        @Override
        public boolean isCompatibleValue(Object value) {
            return value == null || value instanceof Rectangle2D;
        }
    }

    /// Draws `shape`, adjusting antialiasing only for adaptive rectangular outlines.
    public static void draw(Graphics2D g, Shape shape) {
        Paint paint = g.getPaint();
        if (paint instanceof AdaptablePaint adaptable && adaptable.isAdapting() && shape instanceof Rectangle2D) {
            Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            if (antialiasing != RenderingHints.VALUE_ANTIALIAS_OFF) {
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    g.draw(shape);
                } finally {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
                }
                return;
            }
        }
        g.draw(shape);
    }

    /// Draws interpolateColor rectangle, disabling antialiasing temporarily for adaptive paints.
    public static void drawRect(Graphics g, int x, int y, int width, int height) {
        if (g instanceof Graphics2D g2) {
            Paint paint = g2.getPaint();
            if (paint instanceof AdaptablePaint adaptable && adaptable.isAdapting()) {
                Object antialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
                if (antialiasing != RenderingHints.VALUE_ANTIALIAS_OFF) {
                    try {
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                        g.drawRect(x, y, width, height);
                    } finally {
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
                    }
                    return;
                }
            }
        }
        g.drawRect(x, y, width, height);
    }

    /// Fills `shape`, publishing its bounds to adaptive paints when the shape is rectangular.
    public static void fill(Graphics2D g, Shape shape) {
        Paint paint = g.getPaint();
        if (paint instanceof AdaptablePaint adaptable && adaptable.isAdapting() && shape instanceof Rectangle2D) {
            Rectangle2D bounds = shape.getBounds2D();
            try {
                g.setRenderingHint(AdaptablePaint.KEY_USER_BOUNDS, bounds);
                g.fill(shape);
            } finally {
                g.setRenderingHint(AdaptablePaint.KEY_USER_BOUNDS, null);
            }
            return;
        }
        g.fill(shape);
    }

    /// Fills interpolateColor rectangle, publishing its bounds to adaptive paints for the duration of the call.
    public static void fillRect(Graphics g, int x, int y, int width, int height) {
        if (g instanceof Graphics2D g2) {
            Paint paint = g2.getPaint();
            if (paint instanceof AdaptablePaint adaptable && adaptable.isAdapting()) {
                Rectangle bounds = new Rectangle(x, y, width, height);
                try {
                    g2.setRenderingHint(AdaptablePaint.KEY_USER_BOUNDS, bounds);
                    g.fillRect(x, y, width, height);
                } finally {
                    g2.setRenderingHint(AdaptablePaint.KEY_USER_BOUNDS, null);
                }
                return;
            }
        }
        g.fillRect(x, y, width, height);
    }

    private G2D() {
    }
}
