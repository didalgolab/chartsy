package one.chartsy.charting;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.TexturePaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.net.URL;
import java.util.HashMap;
import java.util.Objects;

import one.chartsy.charting.graphic.Marker;
import one.chartsy.charting.internal.PaintPreprocessor;
import one.chartsy.charting.internal.SerializableBasicStroke;
import one.chartsy.charting.util.ArrayPool;
import one.chartsy.charting.util.ColorUtil;
import one.chartsy.charting.util.DrawingPolygon2D;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.IntArrayPool;
import one.chartsy.charting.util.java2d.G2D;
import one.chartsy.charting.util.java2d.LinearGradientPaint;
import one.chartsy.charting.util.java2d.Pattern;
import one.chartsy.charting.util.java2d.RadialGradientPaint;
import one.chartsy.charting.util.java2d.ShapeUtil;
import one.chartsy.charting.util.java2d.Texture;

/// Describes how chart primitives should be stroked, filled, hit-tested, and bounded.
///
/// `PlotStyle` is the charting module's shared rendering-style value object. Renderers retain one
/// or more instances and use them consistently for:
/// - choosing whether a primitive is filled, stroked, or both
/// - supplying the [Stroke], stroke paint, and fill paint used for painting
/// - computing bounds and hit-testing with the same stroke geometry used for painting
/// - adapting some paints to each primitive's bounds when [#isAbsolutePaint()] is `false`
///
/// Construction establishes one of the common style modes:
/// - [#PlotStyle()] enables both stroke and fill with default paints
/// - [#PlotStyle(Paint)] creates a fill-only style
/// - [#PlotStyle(float, Paint)] and [#PlotStyle(Stroke, Paint)] create stroke-only styles
/// - [#PlotStyle(Paint, Paint)] and [#PlotStyle(Stroke, Paint, Paint)] enable both stroke and fill
///
/// Most mutator-style methods such as [#setFillPaint(Paint)], [#setStrokePaint(Paint)],
/// [#setFillOn(boolean)], and [#setStrokeOn(boolean)] are copy-on-write builders that return a
/// shallow copy retaining the same underlying `Paint` and `Stroke` objects. By contrast,
/// [#setAbsolutePaint(boolean)] mutates the receiver in place, and [#applyStroke(Graphics)] /
/// [#restoreStroke(Graphics)] temporarily manipulate a live `Graphics2D`.
///
/// Instances are therefore not thread-safe.
public class PlotStyle implements Cloneable, Serializable {

    /// Describes a reusable transformation that derives one plot style from another.
    ///
    /// Rendering-hint implementations use this abstraction when they want to keep the
    /// renderer-selected geometry semantics while changing a stylistic concern such as
    /// color intensity or fill/stroke paint.
    public static abstract class Change implements Serializable {

        protected Change() {
        }

        /// Applies this transformation to every style slot currently exposed by
        /// `renderer`.
        ///
        /// The underlying style array is updated in place and then handed back to
        /// [ChartRenderer#setStyles(PlotStyle[])], so callers should treat this as a mutating
        /// convenience rather than a pure mapping operation.
        ///
        /// @param renderer the renderer whose style slots should be transformed
        /// @return the same renderer instance after its style slots have been transformed
        public ChartRenderer change(ChartRenderer renderer) {
            PlotStyle[] styles = renderer.getStyles();
            if (styles != null) {
                for (int styleIndex = styles.length - 1; styleIndex >= 0; styleIndex--) {
                    styles[styleIndex] = change(styles[styleIndex]);
                }
                renderer.setStyles(styles);
            }
            return renderer;
        }

        /// Creates a transformed style derived from `style`.
        ///
        /// Implementations typically preserve the source style's geometry flags and
        /// replace only the aspect they own. Callers should therefore expect a result
        /// that remains compatible with the renderer logic that chose the baseline
        /// style in the first place.
        ///
        /// @param style the source style to transform
        /// @return the transformed style
        public abstract PlotStyle change(PlotStyle style);
    }

    /// Base class for style changes driven by a color transformation.
    ///
    /// The default implementation updates only the currently active paint channel of the
    /// source style: fill paint when fill is enabled, otherwise stroke paint when
    /// stroke is enabled. Plain [Color] instances are transformed directly.
    /// [GradientPaint], [LinearGradientPaint], and [RadialGradientPaint] are rebuilt
    /// with transformed color stops while preserving their geometry. [Pattern] keeps
    /// its type and foreground color and transforms only the configured background
    /// color when one is present.
    ///
    /// Unsupported paint implementations are passed through unchanged, so subclasses
    /// can focus purely on the color mapping they want to apply.
    public static abstract class ColorChange extends PlotStyle.Change {

        protected ColorChange() {
        }

        /// Applies the color transformation to the active paint channel of `style`.
        ///
        /// The active channel is fill before stroke. This mirrors the charting
        /// module's normal expectation that fill-enabled styles are primarily
        /// identified by their fill paint.
        ///
        /// @param style the source style to transform
        /// @return a style copy with the transformed fill or stroke paint
        @Override
        public PlotStyle change(PlotStyle style) {
            boolean replaceFillPaint = style.isFillOn();
            Paint paint = replaceFillPaint
                    ? style.getFillPaint()
                    : style.isStrokeOn() ? style.getStrokePaint() : null;

            if (paint instanceof Color color) {
                paint = changeColor(color);
            } else if (paint instanceof Pattern pattern) {
                Color background = pattern.getBackground();
                if (background != null) {
                    paint = new Pattern(pattern.getType(), pattern.getForeground(), changeColor(background));
                }
            } else if (paint instanceof LinearGradientPaint gradient) {
                Color[] colors = gradient.getColors().clone();
                for (int colorIndex = colors.length - 1; colorIndex >= 0; colorIndex--) {
                    colors[colorIndex] = changeColor(colors[colorIndex]);
                }
                paint = new LinearGradientPaint(
                        gradient.getStart(),
                        gradient.getEnd(),
                        gradient.getStops(),
                        colors,
                        gradient.getSpreadMethod(),
                        gradient.isAdapting());
            } else if (paint instanceof RadialGradientPaint gradient) {
                Color[] colors = gradient.getColors().clone();
                for (int colorIndex = colors.length - 1; colorIndex >= 0; colorIndex--) {
                    colors[colorIndex] = changeColor(colors[colorIndex]);
                }
                paint = new RadialGradientPaint(
                        gradient.getCenter(),
                        gradient.getRadius(),
                        gradient.getStops(),
                        colors,
                        gradient.getSpreadMethod(),
                        gradient.isAdapting());
            } else if (paint instanceof GradientPaint gradient) {
                paint = new GradientPaint(
                        gradient.getPoint1(),
                        changeColor(gradient.getColor1()),
                        gradient.getPoint2(),
                        changeColor(gradient.getColor2()),
                        gradient.isCyclic());
            }

            return replaceFillPaint ? style.setFillPaint(paint) : style.setStrokePaint(paint);
        }

        /// Returns the transformed color for one source color sample.
        ///
        /// @param color the source color
        /// @return the transformed color
        protected abstract Color changeColor(Color color);
    }

    /// Internal rendering-hint key used to store PlotStyle's custom anti-aliasing flag on
    /// `Graphics2D`.
    private static final class Drawing2DHintKey extends RenderingHints.Key {
        static final PlotStyle.Drawing2DHintKey INSTANCE = new PlotStyle.Drawing2DHintKey(132306117);

        private Drawing2DHintKey(int privateKey) {
            super(privateKey);
        }

        @Override
        public boolean isCompatibleValue(Object value) {
            return value == PlotStyle.ANTIALIASING_ON || value == PlotStyle.ANTIALIASING_OFF;
        }
    }

    /// Brightens or darkens the active paint channel by repeated small intensity steps.
    ///
    /// Positive levels brighten through [ColorUtil#slightlyBrighter(Color)]. Negative levels darken
    /// through [ColorUtil#slightlyDarker(Color)].
    public static class IntensityChange extends PlotStyle.ColorChange {
        private final int level;

        /// Creates an intensity change with the supplied step count.
        ///
        /// @param level positive to brighten, negative to darken, `0` to leave colors unchanged
        public IntensityChange(int level) {
            this.level = level;
        }

        @Override
        protected Color changeColor(Color color) {
            Color adjustedColor = color;
            if (level > 0) {
                for (int step = 0; step < level; step++) {
                    adjustedColor = ColorUtil.slightlyBrighter(adjustedColor);
                }
            } else if (level < 0) {
                for (int step = 0; step < -level; step++) {
                    adjustedColor = ColorUtil.slightlyDarker(adjustedColor);
                }
            }
            return adjustedColor;
        }

        /// Returns the configured number of intensity steps.
        ///
        /// @return positive for brightening, negative for darkening, `0` for no change
        public int getLevel() {
            return level;
        }
    }

    private static final Object ANTIALIASING_ON = Boolean.TRUE;
    private static final Object ANTIALIASING_OFF = Boolean.FALSE;
    private static final RenderingHints.Key ANTIALIASING_KEY = PlotStyle.Drawing2DHintKey.INSTANCE;
    private static boolean filterOn = true;
    /// Default one-pixel stroke used when no explicit stroke is supplied.
    public static final Stroke DEFAULT_STROKE =
            new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
    /// Default paint used for stroke-only construction paths when `null` is supplied.
    public static final Paint DEFAULT_STROKE_PAINT = Color.black;
    /// Default paint used for fill-enabled construction paths when `null` is supplied.
    public static final Paint DEFAULT_FILL_PAINT = Color.gray;
    static final HashMap<Graphics, Stroke> savedStrokes = new HashMap<>();
    static final HashMap<Graphics, Paint> savedPaints = new HashMap<>();
    static final int SERIALIZED_PAINT = 0;
    static final int PATTERN_PAINT = 1;
    static final int TEXTURE_PAINT = 2;
    static final int NULL_PAINT = 3;

    /// Returns whether `g` currently carries PlotStyle's custom antialiasing hint.
    static boolean isAntialiased(Graphics g) {
        return ((Graphics2D) g).getRenderingHint(PlotStyle.ANTIALIASING_KEY) == PlotStyle.ANTIALIASING_ON;
    }

    /// Installs PlotStyle's custom antialiasing hint on `g`.
    static void setAntialiased(Graphics g, boolean antialiased) {
        Graphics2D g2 = (Graphics2D) g;
        Object hintValue = antialiased ? PlotStyle.ANTIALIASING_ON : PlotStyle.ANTIALIASING_OFF;
        g2.setRenderingHint(PlotStyle.ANTIALIASING_KEY, hintValue);
    }

    /// Gives the active [PaintPreprocessor] a chance to adapt `paint` before drawing.
    private static Paint preprocessPaint(Graphics2D g, Paint paint) {
        PaintPreprocessor preprocessor = (PaintPreprocessor) g.getRenderingHint(PaintPreprocessor.KEY);
        return (preprocessor == null) ? paint : preprocessor.preprocess(paint);
    }

    /// Serializes one retained paint using PlotStyle's compact paint encoding.
    private static void writePaint(ObjectOutputStream out, Paint paint) throws IOException {
        if (paint instanceof Serializable) {
            out.writeInt(SERIALIZED_PAINT);
            out.writeObject(paint);
        } else if (paint instanceof Pattern pattern) {
            out.writeInt(PATTERN_PAINT);
            out.writeInt(pattern.getType());
            out.writeObject(pattern.getForeground());
            out.writeObject(pattern.getBackground());
        } else if (paint instanceof Texture texture) {
            out.writeInt(TEXTURE_PAINT);
            out.writeObject(texture.getImageURL());
            Rectangle2D anchor = texture.getAnchorRect();
            out.writeDouble(anchor.getX());
            out.writeDouble(anchor.getY());
            out.writeDouble(anchor.getWidth());
            out.writeDouble(anchor.getHeight());
        } else {
            out.writeInt(NULL_PAINT);
            out.writeObject(null);
        }
    }

    private static int ARRAYLENGTH(Object array) {
        return Array.getLength(array);
    }

    private static Line2D line(double x1, double y1, double x2, double y2) {
        return new Line2D.Double(x1, y1, x2, y2);
    }

    /// Creates a stroke-only style using [#DEFAULT_STROKE].
    ///
    /// @param strokePaint the stroke paint to retain
    /// @return a new stroke-only style
    public static PlotStyle createStroked(Paint strokePaint) {
        return new PlotStyle(PlotStyle.DEFAULT_STROKE, strokePaint);
    }

    private static Ellipse2D ellipse(double x, double y, double width, double height) {
        return new Ellipse2D.Double(x, y, width, height);
    }

    private static Rectangle2D rectangle(double x, double y, double width, double height) {
        return new Rectangle2D.Double(x, y, width, height);
    }

    /// Returns whether large point-array conversions currently collapse duplicate device pixels.
    ///
    /// When this flag is enabled, several drawing and bounds methods pass a filtering hint into
    /// [GraphicUtil#doubleToInts(int, double[], double[], int[], int[], boolean)] once the input
    /// size grows large enough.
    public static boolean isFilterOn() {
        return PlotStyle.filterOn;
    }

    /// Enables or disables duplicate-device-pixel filtering for large point arrays.
    ///
    /// @param filterOn `true` to enable filtering, `false` to preserve every converted point
    public static void setFilterOn(boolean filterOn) {
        PlotStyle.filterOn = filterOn;
    }

    private transient Stroke stroke;

    private transient Paint strokePaint;

    private transient Paint fillPaint;

    private boolean fillOn;

    private boolean strokeOn;

    private boolean fillPaintIsColor;

    private boolean strokePaintIsColor;

    private boolean absolutePaint;

    private transient Rectangle2D scratchBounds;

    /// Creates a style with both stroke and fill enabled and all default values installed.
    public PlotStyle() {
        this(PlotStyle.DEFAULT_STROKE, PlotStyle.DEFAULT_STROKE_PAINT, PlotStyle.DEFAULT_FILL_PAINT);
    }

    /// Creates a stroke-only style with a new round-join [BasicStroke].
    ///
    /// @param strokeWidth stroke width in user-space units
    /// @param strokePaint stroke paint to retain
    public PlotStyle(float strokeWidth, Paint strokePaint) {
        this(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND), strokePaint);
    }

    /// Creates a fill-only style using default stroke settings.
    ///
    /// The retained stroke still participates in bounds and drawing helpers once stroke is enabled
    /// later through [#setStrokeOn(boolean)].
    ///
    /// @param fillPaint fill paint to retain
    public PlotStyle(Paint fillPaint) {
        this(PlotStyle.DEFAULT_STROKE, PlotStyle.DEFAULT_STROKE_PAINT, fillPaint);
        strokeOn = false;
    }

    /// Creates a style with both stroke and fill enabled using [#DEFAULT_STROKE].
    ///
    /// @param strokePaint stroke paint to retain
    /// @param fillPaint   fill paint to retain
    public PlotStyle(Paint strokePaint, Paint fillPaint) {
        this(PlotStyle.DEFAULT_STROKE, strokePaint, fillPaint);
    }

    /// Creates a stroke-only style using an explicit stroke geometry.
    ///
    /// @param stroke      stroke geometry to retain
    /// @param strokePaint stroke paint to retain
    public PlotStyle(Stroke stroke, Paint strokePaint) {
        this(stroke, strokePaint, PlotStyle.DEFAULT_FILL_PAINT);
        fillOn = false;
    }

    /// Creates a style with both stroke and fill enabled.
    ///
    /// `Stroke` and `Paint` instances are retained by reference.
    ///
    /// @param stroke      stroke geometry to retain
    /// @param strokePaint stroke paint to retain
    /// @param fillPaint   fill paint to retain
    public PlotStyle(Stroke stroke, Paint strokePaint, Paint fillPaint) {
        fillOn = true;
        strokeOn = true;
        fillPaintIsColor = true;
        strokePaintIsColor = true;
        absolutePaint = true;
        scratchBounds = new Rectangle2D.Double();
        setStrokeInternal(stroke);
        setStrokePaintInternal(strokePaint);
        setFillPaintInternal(fillPaint);
    }

    /// Returns the raw line width when the retained stroke is a [BasicStroke].
    float getBasicStrokeWidth() {
        return (!(this.stroke instanceof BasicStroke)) ? 0.0f : ((BasicStroke) stroke).getLineWidth();
    }

    /// Resolves the fill paint to use for a primitive bounded by `(x, y, width, height)`.
    final Paint resolveFillPaint(double x, double y, double width, double height) {
        if (absolutePaint)
            return fillPaint;
        return adaptPaintToBounds(fillPaint, x, y, width, height);
    }

    /// Builds one open path from parallel x/y coordinate arrays.
    private Shape createPath(double[] xPoints, double[] yPoints, int pointCount) {
        GeneralPath path = new GeneralPath();
        path.moveTo((float) xPoints[0], (float) yPoints[0]);
        for (int pointIndex = 1; pointIndex < pointCount; pointIndex++) {
            path.lineTo((float) xPoints[pointIndex], (float) yPoints[pointIndex]);
        }
        return path;
    }

    /// Restores one retained paint from PlotStyle's compact paint encoding.
    private Paint readPaint(ObjectInputStream in) throws IOException, ClassNotFoundException {
        return switch (in.readInt()) {
            case SERIALIZED_PAINT -> (Paint) in.readObject();
            case PATTERN_PAINT -> new Pattern(
                    in.readInt(),
                    (Color) in.readObject(),
                    (Color) in.readObject());
            case TEXTURE_PAINT -> new Texture(
                    (URL) in.readObject(),
                    new Rectangle2D.Double(in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble()));
            case NULL_PAINT -> null;
            default -> null;
        };
    }

    /// Installs the retained stroke paint and refreshes the solid-color fast-path flag.
    final void setStrokePaintInternal(Paint strokePaint) {
        this.strokePaint = (strokePaint != null) ? strokePaint : PlotStyle.DEFAULT_STROKE_PAINT;
        strokePaintIsColor = this.strokePaint instanceof Color;
    }

    /// Re-anchors supported paints to one primitive's bounds when absolute paint is disabled.
    private Paint adaptPaintToBounds(Paint paint, double x, double y, double width, double height) {
        if (paint instanceof Color)
            return paint;
        if (paint instanceof TexturePaint texturePaint) {
            Rectangle2D anchor = texturePaint.getAnchorRect();
            anchor.setRect(x, y, anchor.getWidth(), anchor.getHeight());
            return new TexturePaint(texturePaint.getImage(), anchor);
        }
        if (!(paint instanceof GradientPaint gradientPaint))
            return paint;

        Point2D point1 = gradientPaint.getPoint1();
        Point2D point2 = gradientPaint.getPoint2();
        boolean cyclic = gradientPaint.isCyclic();
        double x1;
        double y1;
        double x2;
        double y2;
        if (point1.getX() == point2.getX()) {
            x1 = x + width / 2.0;
            x2 = x1;
            if (point1.getY() > point2.getY()) {
                y1 = y + height;
                y2 = !cyclic ? y : y + height / 2.0;
            } else {
                y1 = y;
                y2 = !cyclic ? y + height : y + height / 2.0;
            }
        } else if (point1.getY() == point2.getY()) {
            y1 = y + height / 2.0;
            y2 = y1;
            if (point1.getX() > point2.getX()) {
                x1 = x + width;
                x2 = !cyclic ? x : x + width / 2.0;
            } else {
                x1 = x;
                x2 = !cyclic ? x + width : x + width / 2.0;
            }
        } else if (point1.getX() <= point2.getX()) {
            x1 = x;
            x2 = !cyclic ? x + width : x + width / 2.0;
            if (point1.getY() <= point2.getY()) {
                y1 = y;
                y2 = !cyclic ? y + height : y + height / 2.0;
            } else {
                y1 = y + height;
                y2 = !cyclic ? y : y + height / 2.0;
            }
        } else {
            x1 = x + width;
            x2 = !cyclic ? x : x + width / 2.0;
            if (point1.getY() > point2.getY()) {
                y1 = y + height;
                y2 = !cyclic ? y : y + height / 2.0;
            } else {
                y1 = y;
                y2 = !cyclic ? y + height : y + height / 2.0;
            }
        }
        point1.setLocation(x1, y1);
        point2.setLocation(x2, y2);
        return new GradientPaint(point1, gradientPaint.getColor1(), point2, gradientPaint.getColor2(), cyclic);
    }

    /// Returns the stroke hit distance to `shape` under the current stroke geometry.
    private double distanceToStrokedShape(Shape shape, double x, double y, boolean preciseDistance) {
        Shape strokedShape = stroke.createStrokedShape(shape);
        if (preciseDistance) {
            double distance = ShapeUtil.distanceTo(strokedShape, x, y, null);
            if (this.getBasicStrokeWidth() < 4.0f && distance <= 2.0)
                distance = 0.0;
            return distance;
        }
        if (this.getBasicStrokeWidth() >= 4.0f)
            return (!strokedShape.contains(x, y)) ? Double.POSITIVE_INFINITY : 0.0;

        Rectangle2D hitBox = new Rectangle2D.Double(x - 2.0, y - 2.0, 4.0, 4.0);
        return (!strokedShape.intersects(hitBox)) ? Double.POSITIVE_INFINITY : 0.0;
    }

    /// Installs the retained stroke geometry, disabling stroke drawing when `null` is supplied.
    final void setStrokeInternal(Stroke stroke) {
        if (stroke != null)
            this.stroke = stroke;
        else {
            this.stroke = PlotStyle.DEFAULT_STROKE;
            strokeOn = false;
        }
    }

    /// Applies this style's stroke geometry and stroke paint to `g`, preserving the previous
    /// state for [#restoreStroke(Graphics)].
    ///
    /// This helper is intended for callers that issue their own Java2D draw calls but still want
    /// them to respect the current `PlotStyle`.
    public void applyStroke(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        PlotStyle.savedStrokes.put(g2, g2.getStroke());
        PlotStyle.savedPaints.put(g2, g2.getPaint());
        g2.setStroke(stroke);
        g2.setPaint(PlotStyle.preprocessPaint(g2, strokePaint));
    }

    /// Resolves the stroke paint to use for a primitive bounded by `(x, y, width, height)`.
    final Paint resolveStrokePaint(double x, double y, double width, double height) {
        if (absolutePaint)
            return strokePaint;
        return adaptPaintToBounds(strokePaint, x, y, width, height);
    }

    /// Installs the retained fill paint and refreshes the solid-color fast-path flag.
    void setFillPaintInternal(Paint fillPaint) {
        this.fillPaint = (fillPaint != null) ? fillPaint : PlotStyle.DEFAULT_FILL_PAINT;
        fillPaintIsColor = this.fillPaint instanceof Color;
    }

    /// Returns a shallow style copy with a brighter active paint color when possible.
    ///
    /// Only plain `Color` fill or stroke paints are adjusted. More complex paints are retained
    /// unchanged.
    ///
    /// @return a brightened style copy, or `this` when no direct `Color` can be brightened
    public PlotStyle brighter() {
        if (isFillOn())
            if (fillPaintIsColor)
                return setFillPaint(ColorUtil.brighter((Color) fillPaint));
        if (isStrokeOn())
            if (strokePaintIsColor)
                return setStrokePaint(ColorUtil.brighter((Color) strokePaint));
        return this;
    }

    /// Compatibility alias for [#brighter()].
    ///
    /// @return the same result as [#brighter()]
    public PlotStyle brighther() {
        if (isFillOn())
            if (fillPaintIsColor)
                return setFillPaint(ColorUtil.brighter((Color) fillPaint));
        if (isStrokeOn())
            if (strokePaintIsColor)
                return setStrokePaint(ColorUtil.brighter((Color) strokePaint));
        return this;
    }

    /// Returns a shallow clone of this style.
    ///
    /// The clone retains the same `Stroke` and `Paint` object references.
    ///
    /// @return a shallow clone of this style
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }

    /// Returns a shallow copy of this style.
    ///
    /// @return a shallow style copy retaining the same `Stroke` and `Paint` references
    public PlotStyle copy() {
        return (PlotStyle) clone();
    }

    /// Copies only the retained stroke geometry and paints into `destinationStyle`.
    ///
    /// Unlike [#copy()], this helper does not copy the fill/stroke enable flags or the
    /// absolute-paint mode. The destination receives the same retained `Stroke` and `Paint`
    /// references rather than defensive copies.
    public void copyInto(PlotStyle destinationStyle) {
        destinationStyle.stroke = stroke;
        destinationStyle.strokePaint = strokePaint;
        destinationStyle.fillPaint = fillPaint;
    }

    /// Returns a shallow style copy with a darker active paint color when possible.
    ///
    /// @return a darkened style copy, or `this` when no direct `Color` can be darkened
    public PlotStyle darker() {
        if (isFillOn())
            if (fillPaintIsColor)
                return setFillPaint(ColorUtil.darker((Color) fillPaint));
        if (isStrokeOn())
            if (strokePaintIsColor)
                return setStrokePaint(ColorUtil.darker((Color) strokePaint));
        return this;
    }

    /// Returns the hit-test distance from `(x, y)` to the vertex-defined shape described by the
    /// supplied point arrays.
    ///
    /// This higher-level helper respects [#isFillOn()] and [#isStrokeOn()]. When `measureDistance`
    /// is `false`, the method
    /// collapses the answer to either `0.0` or [Double#POSITIVE_INFINITY].
    ///
    /// @return `0.0` for a hit, a positive distance when requested, or
    ///     [Double#POSITIVE_INFINITY] when there is no hit
    public double distanceToPoints(double[] xPoints, double[] yPoints, int pointCount,
                                   double x, double y, boolean measureDistance) {
        double distance = Double.POSITIVE_INFINITY;
        if (isFillOn())
            distance = distanceToPolygon(xPoints, yPoints, pointCount, x, y, measureDistance);
        if (distance > 0.0)
            if (isStrokeOn())
                distance = Math.min(distance, distanceToPolyline(xPoints, yPoints, pointCount, x, y, measureDistance));
        return distance;
    }

    /// Returns the hit-test distance from `(x, y)` to the closed polygon described by the
    /// supplied point arrays.
    ///
    /// This low-level helper ignores [#isFillOn()] and always evaluates the supplied polygon.
    /// Interior hits use an even-odd crossing count. When `measureDistance` is `true`, misses fall
    /// back to the nearest polygon edge.
    ///
    /// @return `0.0` for a hit, a positive distance when requested, or
    ///     [Double#POSITIVE_INFINITY] when there is no hit
    public double distanceToPolygon(double[] xPoints, double[] yPoints, int pointCount,
                                    double x, double y, boolean measureDistance) {
        boolean inside = false;
        for (int currentIndex = 0, previousIndex = pointCount - 1; currentIndex < pointCount; previousIndex = currentIndex++) {
            double currentX = xPoints[currentIndex];
            double currentY = yPoints[currentIndex];
            double previousX = xPoints[previousIndex];
            double previousY = yPoints[previousIndex];
            double crossProduct = (previousY - currentY) * (x - currentX);
            double edgeProduct = (previousX - currentX) * (y - currentY);
            boolean crosses =
                    (currentY <= y && y < previousY && crossProduct < edgeProduct)
                            || (previousY <= y && y < currentY && crossProduct > edgeProduct);
            if (crosses)
                inside = !inside;
        }
        if (inside)
            return 0.0;
        if (!measureDistance)
            return Double.POSITIVE_INFINITY;

        double minimumDistance = Double.POSITIVE_INFINITY;
        for (int currentIndex = 0, previousIndex = pointCount - 1; currentIndex < pointCount; previousIndex = currentIndex++) {
            minimumDistance = Math.min(minimumDistance, ShapeUtil.distanceTo(
                    new Line2D.Double(
                            xPoints[currentIndex],
                            yPoints[currentIndex],
                            xPoints[previousIndex],
                            yPoints[previousIndex]),
                    x,
                    y,
                    null));
        }
        return minimumDistance;
    }

    /// Returns the hit-test distance from `(x, y)` to the stroked polyline described by the
    /// supplied point arrays.
    ///
    /// This low-level helper ignores [#isStrokeOn()] and always evaluates the supplied path using
    /// this style's current stroke geometry.
    ///
    /// @return `0.0` for a hit, a positive distance when requested, or
    ///     [Double#POSITIVE_INFINITY] when there is no hit
    public double distanceToPolyline(double[] xPoints, double[] yPoints, int pointCount,
                                     double x, double y, boolean measureDistance) {
        Shape path = createPath(xPoints, yPoints, pointCount);
        return distanceToStrokedShape(path, x, y, measureDistance);
    }

    /// Returns the hit-test distance from `(x, y)` to `shape` using this style's fill and stroke
    /// rules.
    ///
    /// Unlike [#distanceToPolygon(double[], double[], int, double, double, boolean)] and
    /// [#distanceToPolyline(double[], double[], int, double, double, boolean)], this combined
    /// helper respects both [#isFillOn()] and [#isStrokeOn()].
    ///
    /// @return `0.0` for a hit, a positive distance when requested, or
    ///     [Double#POSITIVE_INFINITY] when there is no hit
    public double distanceToShape(Shape shape, double x, double y, boolean measureDistance) {
        double distance = Double.POSITIVE_INFINITY;
        if (isFillOn()) {
            distance = measureDistance
                    ? ShapeUtil.distanceTo(shape, x, y, null)
                    : (!shape.contains(x, y)) ? Double.POSITIVE_INFINITY : 0.0;
        }
        if (distance > 0.0)
            if (isStrokeOn())
                distance = Math.min(distance, distanceToStrokedShape(shape, x, y, measureDistance));
        return distance;
    }

    /// Draws `shape` using this style's stroke geometry and stroke paint.
    ///
    /// This low-level helper ignores [#isStrokeOn()]. Callers such as [#plotShape(Graphics, Shape)]
    /// decide whether stroking should happen at all. When absolute paint is disabled, supported
    /// non-solid paints are first adapted to the primitive's bounds so each draw call gets its own
    /// local gradient or texture anchoring.
    public void draw(Graphics g, Shape shape) {
        Graphics2D g2 = (Graphics2D) g;
        Stroke previousStroke = g2.getStroke();
        if (strokePaintIsColor)
            g2.setColor((Color) strokePaint);
        else {
            Rectangle2D bounds = shape.getBounds2D();
            g2.setPaint(PlotStyle.preprocessPaint(g2, this.resolveStrokePaint(
                    bounds.getX(),
                    bounds.getY(),
                    bounds.getWidth(),
                    bounds.getHeight())));
        }
        g2.setStroke(stroke);
        if (shape instanceof Line2D line) {
            g2.drawLine(
                    GraphicUtil.toInt(line.getX1()),
                    GraphicUtil.toInt(line.getY1()),
                    GraphicUtil.toInt(line.getX2()),
                    GraphicUtil.toInt(line.getY2()));
        } else {
            G2D.draw(g2, shape);
        }
        g2.setStroke(previousStroke);
    }

    /// Draws one stroked line segment expressed in double-precision user coordinates.
    ///
    /// As with [#draw(Graphics, Shape)], this helper ignores [#isStrokeOn()]. Anti-aliased
    /// rendering uses a geometric [Line2D]. Non-anti-aliased rendering snaps the coordinates to
    /// device pixels first.
    public void drawLine(Graphics g, double x1, double y1, double x2, double y2) {
        if (PlotStyle.isAntialiased(g))
            draw(g, PlotStyle.line(x1, y1, x2, y2));
        else
            this.drawLine(g, GraphicUtil.toInt(x1), GraphicUtil.toInt(y1), GraphicUtil.toInt(x2),
                    GraphicUtil.toInt(y2));
    }

    /// Draws one stroked line segment expressed in device-space integer coordinates.
    ///
    /// When absolute paint is disabled, the stroke paint is anchored to the line segment's bounds.
    public void drawLine(Graphics g, int x1, int y1, int x2, int y2) {
        Graphics2D g2 = (Graphics2D) g;
        Stroke previousStroke = g2.getStroke();
        if (strokePaintIsColor)
            g2.setColor((Color) strokePaint);
        else if (absolutePaint)
            g2.setPaint(PlotStyle.preprocessPaint(g2, strokePaint));
        else {
            int paintX = Math.min(x1, x2);
            int paintY = Math.min(y1, y2);
            int paintWidth = Math.abs(x2 - x1);
            int paintHeight = Math.abs(y2 - y1);
            g2.setPaint(PlotStyle.preprocessPaint(g2, this.resolveStrokePaint(
                    paintX,
                    paintY,
                    paintWidth,
                    paintHeight)));
        }
        g2.setStroke(stroke);
        g2.drawLine(x1, y1, x2, y2);
        g2.setStroke(previousStroke);
    }

    /// Draws one stroked oval outline expressed in double-precision user coordinates.
    public void drawOval(Graphics g, double x, double y, double width, double height) {
        if (PlotStyle.isAntialiased(g))
            draw(g, PlotStyle.ellipse(x, y, width, height));
        else
            this.drawOval(g, GraphicUtil.toInt(x), GraphicUtil.toInt(y), GraphicUtil.toInt(width),
                    GraphicUtil.toInt(height));
    }

    /// Draws one stroked oval outline expressed in device-space integer coordinates.
    ///
    /// When absolute paint is disabled, the stroke paint is anchored to the oval bounds.
    public void drawOval(Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g;
        Stroke previousStroke = g2.getStroke();
        if (strokePaintIsColor)
            g2.setColor((Color) strokePaint);
        else
            g2.setPaint(PlotStyle.preprocessPaint(g2, this.resolveStrokePaint(x, y, width, height)));
        g2.setStroke(stroke);
        g2.drawOval(x, y, width, height);
        g2.setStroke(previousStroke);
    }

    /// Draws a polyline described by double-precision coordinate arrays.
    ///
    /// Large non-anti-aliased inputs may be filtered through the global [#isFilterOn()] setting to
    /// collapse duplicate device pixels before drawing.
    public void drawPolyline(Graphics g, double[] xPoints, double[] yPoints, int pointCount) {
        if (!PlotStyle.isAntialiased(g)) {
            Object intCoordsLock = ArrayPool.getIntCoordsLock();
            synchronized (intCoordsLock) {
                int[][] intCoords = ArrayPool.allocIntCoords(pointCount);
                boolean filterDuplicatePixels = pointCount > 100 && PlotStyle.filterOn;
                int retainedPointCount = GraphicUtil.doubleToInts(
                        pointCount,
                        xPoints,
                        yPoints,
                        intCoords[0],
                        intCoords[1],
                        filterDuplicatePixels);
                this.drawPolyline(g, intCoords[0], intCoords[1], retainedPointCount);
            }
        } else {
            synchronized (DrawingPolygon2D.getClassLock()) {
                boolean closed = pointCount > 1
                        && xPoints[0] == xPoints[pointCount - 1]
                        && yPoints[0] == yPoints[pointCount - 1];
                draw(g, DrawingPolygon2D.getInstance(xPoints, yPoints, pointCount, closed));
            }
        }
    }

    /// Draws a polyline and then paints `marker` at every retained device-space vertex.
    ///
    /// Marker positions follow the converted point sequence, so any duplicate-pixel filtering
    /// applied during coordinate conversion also affects marker placement.
    public void drawPolyline(Graphics g, double[] xPoints, double[] yPoints, int pointCount,
                             Marker marker, int markerSize) {
        int[] xCoords = IntArrayPool.take(pointCount);
        int[] yCoords = IntArrayPool.take(pointCount);
        try {
            boolean filterDuplicatePixels = pointCount > 100 && PlotStyle.filterOn;
            int retainedPointCount = GraphicUtil.doubleToInts(
                    pointCount,
                    xPoints,
                    yPoints,
                    xCoords,
                    yCoords,
                    filterDuplicatePixels);
            this.drawPolyline(g, xCoords, yCoords, retainedPointCount);
            if (marker != null) {
                for (int pointIndex = 0; pointIndex < retainedPointCount; pointIndex++) {
                    marker.draw(g, xCoords[pointIndex], yCoords[pointIndex], markerSize, this);
                }
            }
        } finally {
            IntArrayPool.release(xCoords);
            IntArrayPool.release(yCoords);
        }
    }

    /// Draws a polyline described by device-space integer coordinate arrays.
    public void drawPolyline(Graphics g, int[] xPoints, int[] yPoints, int pointCount) {
        Graphics2D g2 = (Graphics2D) g;
        Stroke previousStroke = g2.getStroke();
        if (strokePaintIsColor)
            g2.setColor((Color) strokePaint);
        else {
            scratchBounds = this.getBounds(xPoints, yPoints, pointCount, false, true, scratchBounds);
            g2.setPaint(PlotStyle.preprocessPaint(g2, this.resolveStrokePaint(
                    scratchBounds.getX(), scratchBounds.getY(), scratchBounds.getWidth(), scratchBounds.getHeight())));
        }
        g2.setStroke(stroke);
        drawPolylineAsDouble(g2, xPoints, yPoints, pointCount);
        g2.setStroke(previousStroke);
    }

    private static void drawPolylineAsDouble(Graphics2D g2, int[] xPoints, int[] yPoints, int nPoints) {
        int limit = Math.min(nPoints, Math.min(xPoints.length, yPoints.length));
        if (limit < 2)
            return;

        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO, limit);
        path.moveTo(0.5 + xPoints[0], 0.5 + yPoints[0]);
        for (int i = 1; i < limit; i++) {
            path.lineTo(0.5 + xPoints[i], 0.5 + yPoints[i]);
        }
        g2.draw(path);
    }

    /// Draws one rectangle outline expressed in double-precision user coordinates.
    public void drawRect(Graphics g, double x, double y, double width, double height) {
        if (PlotStyle.isAntialiased(g))
            draw(g, PlotStyle.rectangle(x, y, width, height));
        else
            this.drawRect(g, GraphicUtil.toInt(x), GraphicUtil.toInt(y), GraphicUtil.toInt(width),
                    GraphicUtil.toInt(height));
    }

    /// Draws one rectangle outline expressed in device-space integer coordinates.
    public void drawRect(Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g;
        Stroke previousStroke = g2.getStroke();
        if (strokePaintIsColor)
            g2.setColor((Color) strokePaint);
        else
            g2.setPaint(PlotStyle.preprocessPaint(g2, this.resolveStrokePaint(x, y, width, height)));
        g2.setStroke(stroke);
        G2D.drawRect(g2, x, y, width, height);
        g2.setStroke(previousStroke);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof PlotStyle other))
            return false;
        return fillOn == other.fillOn
                && strokeOn == other.strokeOn
                && absolutePaint == other.absolutePaint
                && stroke.equals(other.stroke)
                && strokePaint.equals(other.strokePaint)
                && fillPaint.equals(other.fillPaint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stroke, strokePaint, fillPaint, fillOn, strokeOn, absolutePaint);
    }

    /// Expands `bounds` in place so it encloses the area this style would stroke.
    ///
    /// When `forceExactBounds` is `false` and [#quickBounds()] is `true`, the method uses a cheap
    /// `BasicStroke`-based approximation. Otherwise it measures the exact stroked outline.
    public void expand(boolean forceExactBounds, Rectangle2D bounds) {
        if (!forceExactBounds && quickBounds()) {
            BasicStroke basicStroke = (BasicStroke) stroke;
            double strokeExpansion = basicStroke.getLineWidth();
            if (strokeExpansion <= 1.0)
                return;
            if (basicStroke.getLineJoin() == BasicStroke.JOIN_MITER)
                strokeExpansion *= basicStroke.getMiterLimit() / 2.0f;
            GraphicUtil.grow(bounds, strokeExpansion, strokeExpansion);
            return;
        }
        bounds.setRect(ShapeUtil.getTightBounds2D(stroke.createStrokedShape(bounds)));
    }

    /// Expands `bounds` using the default fast-if-possible stroke-bounds path.
    public final void expand(Rectangle2D bounds) {
        this.expand(false, bounds);
    }

    /// Fills `shape` using this style's fill paint.
    ///
    /// As with [#draw(Graphics, Shape)], supported non-solid paints are adapted to the primitive's
    /// bounds when absolute paint is disabled.
    public void fill(Graphics g, Shape shape) {
        Graphics2D g2 = (Graphics2D) g;
        if (fillPaintIsColor)
            g2.setColor((Color) fillPaint);
        else {
            Rectangle2D shapeBounds = shape.getBounds2D();
            g2.setPaint(PlotStyle.preprocessPaint(g2, this.resolveFillPaint(
                    shapeBounds.getX(), shapeBounds.getY(), shapeBounds.getWidth(), shapeBounds.getHeight())));
        }
        G2D.fill(g2, shape);
    }

    /// Fills one oval expressed in double-precision user coordinates.
    public void fillOval(Graphics g, double x, double y, double width, double height) {
        if (PlotStyle.isAntialiased(g))
            fill(g, PlotStyle.ellipse(x, y, width, height));
        else
            this.fillOval(g, GraphicUtil.toInt(x), GraphicUtil.toInt(y), GraphicUtil.toInt(width),
                    GraphicUtil.toInt(height));
    }

    /// Fills one oval expressed in device-space integer coordinates.
    public void fillOval(Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g;
        if (fillPaintIsColor)
            g2.setColor((Color) fillPaint);
        else
            g2.setPaint(PlotStyle.preprocessPaint(g2, this.resolveFillPaint(x, y, width, height)));
        g2.fillOval(x, y, width, height);
    }

    /// Fills the closed polygon described by double-precision coordinate arrays.
    public void fillPolygon(Graphics g, double[] xPoints, double[] yPoints, int pointCount) {
        if (PlotStyle.isAntialiased(g)) {
            synchronized (DrawingPolygon2D.getClassLock()) {
                fill(g, DrawingPolygon2D.getInstance(xPoints, yPoints, pointCount, true));
            }
        } else {
            Object coordinateLock = ArrayPool.getIntCoordsLock();
            synchronized (coordinateLock) {
                int[][] intCoordinates = ArrayPool.allocIntCoords(pointCount);
                boolean applyFiltering = pointCount > 100 && PlotStyle.filterOn;
                int retainedPointCount = GraphicUtil.doubleToInts(
                        pointCount,
                        xPoints,
                        yPoints,
                        intCoordinates[0],
                        intCoordinates[1],
                        applyFiltering);
                this.fillPolygon(g, intCoordinates[0], intCoordinates[1], retainedPointCount);
            }
        }
    }

    /// Fills the closed polygon described by device-space integer coordinate arrays.
    public void fillPolygon(Graphics g, int[] xPoints, int[] yPoints, int pointCount) {
        Graphics2D g2 = (Graphics2D) g;
        if (fillPaintIsColor)
            g2.setColor((Color) fillPaint);
        else {
            scratchBounds = this.getBounds(xPoints, yPoints, pointCount, true, true, scratchBounds);
            g2.setPaint(PlotStyle.preprocessPaint(g2, this.resolveFillPaint(
                    scratchBounds.getX(),
                    scratchBounds.getY(),
                    scratchBounds.getWidth(),
                    scratchBounds.getHeight())));
        }
        g2.fillPolygon(xPoints, yPoints, pointCount);
    }

    /// Fills one rectangle expressed in device-space integer coordinates.
    public void fillRect(Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g;
        if (fillPaintIsColor)
            g2.setColor((Color) fillPaint);
        else
            g2.setPaint(PlotStyle.preprocessPaint(g2, this.resolveFillPaint(x, y, width, height)));
        G2D.fillRect(g2, x, y, width, height);
    }

    /// Returns bounds for the primitive described by double-precision coordinate arrays.
    ///
    /// The doubles are first snapped to device-space integers and then delegated to
    /// [#getBounds(int[], int[], int, boolean, boolean, Rectangle2D)], so the result follows the
    /// same repaint-oriented device geometry as the corresponding draw paths rather than exact
    /// source-space coordinates.
    ///
    /// The method may reuse `bounds` as the destination rectangle.
    public Rectangle2D getBounds(double[] xPoints, double[] yPoints, int pointCount,
                                 boolean closed, boolean expandStroke, Rectangle2D bounds) {
        if (pointCount != 0) {
            Object coordinateLock = ArrayPool.getIntCoordsLock();
            synchronized (coordinateLock) {
                int[][] intCoordinates = ArrayPool.allocIntCoords(pointCount);
                boolean applyFiltering = pointCount > 100 && PlotStyle.filterOn;
                int retainedPointCount = GraphicUtil.doubleToInts(
                        pointCount,
                        xPoints,
                        yPoints,
                        intCoordinates[0],
                        intCoordinates[1],
                        applyFiltering);
                return this.getBounds(intCoordinates[0], intCoordinates[1], retainedPointCount, closed, expandStroke, bounds);
            }
        }
        if (bounds == null)
            return new Rectangle2D.Double();
        else
            bounds.setRect(0.0, 0.0, 0.0, 0.0);
        return bounds;
    }

    /// Returns polygon-style bounds for the supplied double-precision coordinates.
    public final Rectangle2D getBounds(double[] xPoints, double[] yPoints, int pointCount, Rectangle2D bounds) {
        return this.getBounds(xPoints, yPoints, pointCount, true, false, bounds);
    }

    /// Returns bounds for the primitive described by device-space integer coordinate arrays.
    ///
    /// This overload is the workhorse used by drawing paths that already snapped to device pixels.
    /// It uses a cheap `BasicStroke` estimate when possible and falls back to exact stroked-shape
    /// bounds otherwise.
    public Rectangle2D getBounds(int[] xPoints, int[] yPoints, int pointCount,
                                 boolean closed, boolean expandStroke, Rectangle2D bounds) {
        if (pointCount == 0) {
            if (bounds == null)
                return new Rectangle2D.Double();
            bounds.setRect(0.0, 0.0, 0.0, 0.0);
            return bounds;
        }

        if (stroke instanceof BasicStroke basicStroke) {
            boolean requiresExactShapeBounds = expandStroke
                    && basicStroke.getLineWidth() > 1.0f
                    && basicStroke.getLineJoin() == BasicStroke.JOIN_MITER;
            if (!requiresExactShapeBounds) {
                double minX = xPoints[0];
                double maxX = minX;
                double minY = yPoints[0];
                double maxY = minY;
                for (int pointIndex = 1; pointIndex < pointCount; pointIndex++) {
                    double pointX = xPoints[pointIndex];
                    double pointY = yPoints[pointIndex];
                    if ((int) pointX < (int) minX)
                        minX = pointX;
                    if ((int) pointX > (int) maxX)
                        maxX = pointX;
                    if ((int) pointY < (int) minY)
                        minY = pointY;
                    if ((int) pointY > (int) maxY)
                        maxY = pointY;
                }

                double boundsX;
                double boundsY;
                double boundsMaxX;
                double boundsMaxY;
                float lineWidth = basicStroke.getLineWidth();
                if (lineWidth <= 1.0f) {
                    boundsX = minX;
                    boundsY = minY;
                    boundsMaxX = (int) maxX + 1;
                    boundsMaxY = (int) maxY + 1;
                } else {
                    double strokeExpansion = basicStroke.getLineJoin() != BasicStroke.JOIN_MITER
                            ? 0.7071067811865476 * lineWidth
                            : 0.5 * lineWidth * basicStroke.getMiterLimit();
                    boundsX = minX + 0.5 - strokeExpansion;
                    boundsY = minY + 0.5 - strokeExpansion;
                    boundsMaxX = maxX + 0.5 + strokeExpansion;
                    boundsMaxY = maxY + 0.5 + strokeExpansion;
                }

                if (bounds == null)
                    return new Rectangle2D.Double(boundsX, boundsY, boundsMaxX - boundsX, boundsMaxY - boundsY);
                bounds.setRect(boundsX, boundsY, boundsMaxX - boundsX, boundsMaxY - boundsY);
                return bounds;
            }
        }

        double[] shapeXPoints = new double[pointCount];
        double[] shapeYPoints = new double[pointCount];
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            shapeXPoints[pointIndex] = xPoints[pointIndex] + 0.5;
            shapeYPoints[pointIndex] = yPoints[pointIndex] + 0.5;
        }
        synchronized (DrawingPolygon2D.getClassLock()) {
            DrawingPolygon2D shape = DrawingPolygon2D.getInstance(shapeXPoints, shapeYPoints, pointCount, closed);
            Rectangle2D exactBounds = getShapeBounds(shape);
            if (bounds != null) {
                bounds.setRect(exactBounds);
                return bounds;
            }
            return exactBounds;
        }
    }

    /// Returns polygon-style bounds for the supplied device-space coordinates.
    public final Rectangle2D getBounds(int[] xPoints, int[] yPoints, int pointCount, Rectangle2D bounds) {
        return this.getBounds(xPoints, yPoints, pointCount, true, false, bounds);
    }

    /// Returns the retained fill color when the fill paint is a plain [Color].
    ///
    /// @return the retained fill color, or `null` when the fill paint is not a `Color`
    public Color getFillColor() {
        return fillPaintIsColor ? (Color) fillPaint : null;
    }

    /// Returns the retained fill paint reference.
    public Paint getFillPaint() {
        return fillPaint;
    }

    /// Returns stroke bounds for `shape`.
    ///
    /// Degenerate zero-width or zero-height results are normalized to at least one device unit so
    /// callers can still repaint or hit-test the returned rectangle meaningfully.
    public Rectangle2D getShapeBounds(Shape shape) {
        Rectangle2D bounds;
        if (shape instanceof Rectangle2D rectangle && stroke.getClass() == BasicStroke.class) {
            float lineWidth = ((BasicStroke) stroke).getLineWidth();
            bounds = new Rectangle2D.Double(
                    rectangle.getX() - 0.5f * lineWidth,
                    rectangle.getY() - 0.5f * lineWidth,
                    rectangle.getWidth() + lineWidth,
                    rectangle.getHeight() + lineWidth);
        } else {
            bounds = ShapeUtil.getTightBounds2D(stroke.createStrokedShape(shape));
        }

        if (bounds.getWidth() == 0.0 || bounds.getHeight() == 0.0) {
            bounds = new Rectangle2D.Double(
                    bounds.getX(),
                    bounds.getY(),
                    bounds.getWidth() == 0.0 ? 1.0 : bounds.getWidth(),
                    bounds.getHeight() == 0.0 ? 1.0 : bounds.getHeight());
        }
        return bounds;
    }

    /// Returns the retained stroke geometry reference.
    public Stroke getStroke() {
        return stroke;
    }

    /// Returns the retained stroke color when the stroke paint is a plain [Color].
    ///
    /// @return the retained stroke color, or `null` when the stroke paint is not a `Color`
    public Color getStrokeColor() {
        return strokePaintIsColor ? (Color) strokePaint : null;
    }

    /// Returns the retained stroke paint reference.
    public Paint getStrokePaint() {
        return strokePaint;
    }

    /// Returns whether supported non-solid paints keep their original coordinates.
    ///
    /// When this flag is `false`, PlotStyle re-anchors some paints to each primitive's bounds
    /// before painting it.
    public final boolean isAbsolutePaint() {
        return absolutePaint;
    }

    /// Returns whether fill operations are enabled for this style.
    public final boolean isFillOn() {
        return fillOn;
    }

    /// Returns whether stroke operations are enabled for this style.
    public final boolean isStrokeOn() {
        return strokeOn;
    }

    /// Paints a circle centered at `(centerX, centerY)` with radius `radius`.
    ///
    /// Fill paths use a `(2r + 1)` diameter so the center pixel remains covered, while stroke paths
    /// use a `2r` outline diameter.
    public void plotCircle(Graphics g, int centerX, int centerY, int radius) {
        int x = centerX - radius;
        int y = centerY - radius;
        int diameter = radius << 1;
        if (isFillOn())
            this.fillOval(g, x, y, diameter + 1, diameter + 1);
        if (isStrokeOn())
            this.drawOval(g, x, y, diameter, diameter);
    }

    /// Paints one oval using this style's fill and stroke enable flags.
    public void plotOval(Graphics g, int x, int y, int width, int height) {
        if (isFillOn())
            this.fillOval(g, x, y, width, height);
        if (isStrokeOn())
            this.drawOval(g, x, y, width, height);
    }

    /// Paints the vertex-defined shape described by double-precision coordinate arrays.
    public void plotPoints(Graphics g, double[] xPoints, double[] yPoints, int pointCount) {
        if (PlotStyle.isAntialiased(g)) {
            synchronized (DrawingPolygon2D.getClassLock()) {
                plotShape(g, DrawingPolygon2D.getInstance(xPoints, yPoints, pointCount, isFillOn()));
            }
        } else {
            Object coordinateLock = ArrayPool.getIntCoordsLock();
            synchronized (coordinateLock) {
                int[][] intCoordinates = ArrayPool.allocIntCoords(pointCount);
                boolean applyFiltering = pointCount > 100 && PlotStyle.filterOn;
                int retainedPointCount = GraphicUtil.doubleToInts(
                        pointCount,
                        xPoints,
                        yPoints,
                        intCoordinates[0],
                        intCoordinates[1],
                        applyFiltering);
                this.plotPoints(g, intCoordinates[0], intCoordinates[1], retainedPointCount);
            }
        }
    }

    /// Paints the vertex-defined shape described by device-space integer coordinate arrays.
    public void plotPoints(Graphics g, int[] xPoints, int[] yPoints, int pointCount) {
        if (isFillOn())
            this.fillPolygon(g, xPoints, yPoints, pointCount);
        if (isStrokeOn())
            this.drawPolyline(g, xPoints, yPoints, pointCount);
    }

    /// Paints one rectangle described by two opposing corners in double-precision coordinates.
    ///
    /// Unlike the integer overload, `x2` and `y2` denote the opposite corner rather than width and
    /// height. The coordinates are first snapped to device pixels and then normalized into a
    /// top-left corner plus size.
    public void plotRect(Graphics g, double x1, double y1, double x2, double y2) {
        int width = 1;
        int height = 1;
        int left = GraphicUtil.toInt(x1);
        int top = GraphicUtil.toInt(y1);
        int right = GraphicUtil.toInt(x2);
        int bottom = GraphicUtil.toInt(y2);
        if (right < left) {
            width = left - right;
            left = right;
        } else if (right > left)
            width = right - left;
        if (bottom < top) {
            height = top - bottom;
            top = bottom;
        } else if (bottom > top)
            height = bottom - top;
        this.plotRect(g, left, top, width, height);
    }

    /// Paints one rectangle expressed in device-space coordinates.
    public void plotRect(Graphics g, int x, int y, int width, int height) {
        if (isFillOn())
            fillRect(g, x, y, width, height);
        if (isStrokeOn())
            this.drawRect(g, x, y, width, height);
    }

    /// Paints `shape` by filling it, stroking it, or both according to this style's enable flags.
    public void plotShape(Graphics g, Shape shape) {
        if (isFillOn())
            fill(g, shape);
        if (isStrokeOn())
            draw(g, shape);
    }

    /// Paints a square centered at `(centerX, centerY)` with half-size `halfSize`.
    ///
    /// Fill paths use a `(2s + 1)` side length so the center pixel remains covered, while stroke
    /// paths use a `2s` outline side length.
    public void plotSquare(Graphics g, int centerX, int centerY, int halfSize) {
        int x = centerX - halfSize;
        int y = centerY - halfSize;
        int sideLength = halfSize << 1;
        if (isFillOn())
            fillRect(g, x, y, sideLength + 1, sideLength + 1);
        if (isStrokeOn())
            this.drawRect(g, x, y, sideLength, sideLength);
    }

    /// Returns whether `(x, y)` hits the vertex-defined shape described by the supplied point
    /// arrays.
    public boolean pointsContains(double[] xPoints, double[] yPoints, int pointCount, double x, double y) {
        return distanceToPoints(xPoints, yPoints, pointCount, x, y, false) == 0.0;
    }

    /// Returns whether `(x, y)` lies inside or on the edge of the closed polygon described by the
    /// supplied point arrays.
    public boolean polygonContains(double[] xPoints, double[] yPoints, int pointCount, double x, double y) {
        return distanceToPolygon(xPoints, yPoints, pointCount, x, y, false) == 0.0;
    }

    /// Returns whether `(x, y)` hits the stroked polyline described by the supplied point arrays.
    public boolean polylineContains(double[] xPoints, double[] yPoints, int pointCount, double x, double y) {
        return distanceToPolyline(xPoints, yPoints, pointCount, x, y, false) == 0.0;
    }

    /// Returns whether stroke bounds can be expanded from simple `BasicStroke` parameters alone.
    ///
    /// Callers use this to choose between a fast repaint/bounds estimate and the more expensive
    /// exact stroked-shape path.
    public boolean quickBounds() {
        return stroke == PlotStyle.DEFAULT_STROKE || this.stroke instanceof BasicStroke;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        strokePaint = readPaint(in);
        fillPaint = readPaint(in);
        Object serializedStroke = in.readObject();
        if (serializedStroke == null)
            serializedStroke = PlotStyle.DEFAULT_STROKE;
        else if (serializedStroke instanceof SerializableBasicStroke serializableStroke)
            serializedStroke = serializableStroke.getStroke();
        stroke = (Stroke) serializedStroke;
        strokePaintIsColor = strokePaint instanceof Color;
        fillPaintIsColor = fillPaint instanceof Color;
        scratchBounds = new Rectangle2D.Double();
    }

    /// Restores the `Graphics2D` state previously captured by [#applyStroke(Graphics)].
    ///
    /// If `applyStroke(...)` was not called for `g`, the method leaves `g` unchanged.
    public void restoreStroke(Graphics g) {
        Stroke previousStroke = PlotStyle.savedStrokes.remove(g);
        if (previousStroke != null)
            ((Graphics2D) g).setStroke(previousStroke);
        Paint previousPaint = PlotStyle.savedPaints.remove(g);
        if (previousPaint != null)
            ((Graphics2D) g).setPaint(previousPaint);
    }

    /// Switches between absolute paint coordinates and per-primitive paint anchoring.
    ///
    /// Unlike the other `set*` style methods, this method mutates the receiver in place.
    public void setAbsolutePaint(boolean absolutePaint) {
        this.absolutePaint = absolutePaint;
    }

    /// Returns a style copy with fill drawing enabled or disabled.
    ///
    /// The returned style reuses the same retained paints and stroke object.
    public PlotStyle setFillOn(boolean fillOn) {
        if (fillOn == this.fillOn)
            return this;
        PlotStyle updatedStyle = copy();
        updatedStyle.fillOn = fillOn;
        return updatedStyle;
    }

    /// Returns a style copy retaining `paint` as the fill paint.
    ///
    /// The supplied `Paint` instance is retained by reference.
    public PlotStyle setFillPaint(Paint paint) {
        PlotStyle updatedStyle = copy();
        updatedStyle.setFillPaintInternal(paint);
        return updatedStyle;
    }

    /// Returns a style copy retaining `stroke` as the stroke geometry.
    ///
    /// The supplied `Stroke` instance is retained by reference.
    public PlotStyle setStroke(Stroke stroke) {
        PlotStyle updatedStyle = copy();
        updatedStyle.setStrokeInternal(stroke);
        return updatedStyle;
    }

    /// Returns a style copy retaining `stroke` as the stroke geometry and `paint` as the stroke
    /// paint.
    ///
    /// Both supplied objects are retained by reference.
    public PlotStyle setStroke(Stroke stroke, Paint paint) {
        PlotStyle updatedStyle = copy();
        updatedStyle.setStrokePaintInternal(paint);
        updatedStyle.setStrokeInternal(stroke);
        return updatedStyle;
    }

    /// Returns a style copy with stroke drawing enabled or disabled.
    ///
    /// The returned style reuses the same retained paints and stroke object.
    public PlotStyle setStrokeOn(boolean strokeOn) {
        if (strokeOn == this.strokeOn)
            return this;
        PlotStyle updatedStyle = copy();
        updatedStyle.strokeOn = strokeOn;
        return updatedStyle;
    }

    /// Returns a style copy retaining `paint` as the stroke paint.
    ///
    /// The supplied `Paint` instance is retained by reference.
    public PlotStyle setStrokePaint(Paint paint) {
        PlotStyle updatedStyle = copy();
        updatedStyle.setStrokePaintInternal(paint);
        return updatedStyle;
    }

    /// Returns whether `(x, y)` hits `shape` under this style's fill and stroke rules.
    public boolean shapeContains(Shape shape, double x, double y) {
        return distanceToShape(shape, x, y, false) == 0.0;
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        PlotStyle.writePaint(out, strokePaint);
        PlotStyle.writePaint(out, fillPaint);
        if (this.stroke instanceof Serializable)
            out.writeObject(stroke);
        else if (!(this.stroke instanceof BasicStroke))
            out.writeObject(null);
        else
            out.writeObject(new SerializableBasicStroke((BasicStroke) stroke));
    }
}
