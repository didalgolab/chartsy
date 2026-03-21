package one.chartsy.charting.util.java2d;

import java.awt.Color;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;

/// Gradient paint that interpolates colors outward from a circle.
///
/// The gradient is defined by a center point, a radius, and a focal point. When the focal point
/// differs from the center, color interpolation follows rays that start at the focus and terminate
/// on the outer circle.
///
/// As with [LinearGradientPaint], enabling [#isAdapting()] makes the geometry relative to the
/// current user bounds supplied through [AdaptablePaint#KEY_USER_BOUNDS], which allows normalized
/// `0..1` radial gradients to be reused across differently sized chart elements.
public class RadialGradientPaint extends MultipleGradientPaint {
    private final Point2D.Float centerPoint;
    private final float radius;
    private final Point2D.Float focusPoint;
    
    /// Creates an sRGB radial gradient whose focus coincides with its center.
    public RadialGradientPaint(float centerX, float centerY, float radius, float[] stops, Color[] colors, boolean adapting) {
        this(new Point2D.Float(centerX, centerY), radius, stops, colors, new Point2D.Float(centerX, centerY), adapting);
    }
    
    /// Creates an sRGB radial gradient with an explicit focal point.
    public RadialGradientPaint(float centerX, float centerY, float radius, float[] stops, Color[] colors, float focusX, float focusY,
            boolean adapting) {
        this(new Point2D.Float(centerX, centerY), radius, stops, colors, new Point2D.Float(focusX, focusY), adapting);
    }
    
    /// Creates an sRGB radial gradient with explicit focus and spread behavior.
    public RadialGradientPaint(float centerX, float centerY, float radius, float[] stops, Color[] colors, float focusX, float focusY,
            short spreadMethod, boolean adapting) {
        this(new Point2D.Float(centerX, centerY), radius, stops, colors, new Point2D.Float(focusX, focusY), spreadMethod, adapting);
    }
    
    /// Creates an sRGB radial gradient with explicit spread behavior and a centered focus.
    public RadialGradientPaint(float centerX, float centerY, float radius, float[] stops, Color[] colors, short spreadMethod, boolean adapting) {
        this(new Point2D.Float(centerX, centerY), radius, stops, colors, new Point2D.Float(centerX, centerY), spreadMethod, adapting);
    }
    
    /// Creates an sRGB radial gradient whose focus coincides with its center.
    public RadialGradientPaint(Point2D center, float radius, float[] stops, Color[] colors, boolean adapting) {
        this(center, radius, stops, colors, center, adapting);
    }
    
    /// Creates an sRGB radial gradient with an explicit focal point.
    public RadialGradientPaint(Point2D center, float radius, float[] stops, Color[] colors, Point2D focal, boolean adapting) {
        this(center, radius, stops, colors, focal, (short) 1, adapting);
    }
    
    /// Creates an sRGB radial gradient with explicit focus and spread behavior.
    public RadialGradientPaint(Point2D center, float radius, float[] stops, Color[] colors, Point2D focal, short spreadMethod,
            boolean adapting) {
        this(center, radius, stops, colors, focal, spreadMethod, (short) 0, null, adapting);
    }
    
    /// Creates a radial gradient with explicit focus, spread mode, interpolation space, and extra
    /// transform.
    ///
    /// The center and focal points are copied on construction. When `adapting` is `true`, the
    /// geometry is first normalized to the current user bounds and `transform` is applied
    /// afterwards.
    ///
    /// @throws IllegalArgumentException if `center` or `focal` is `null`, or if `radius` is zero
    public RadialGradientPaint(Point2D center, float radius, float[] stops, Color[] colors, Point2D focal, short spreadMethod,
            short colorSpace, AffineTransform transform, boolean adapting) {
        super(stops, colors, spreadMethod, colorSpace, transform, adapting);
        if (center == null)
            throw new IllegalArgumentException("center must not be null");
        if (focal == null)
            throw new IllegalArgumentException("focal must not be null");
        if (radius == 0.0f)
            throw new IllegalArgumentException("radius must not be zero");
        centerPoint = new Point2D.Float((float) center.getX(), (float) center.getY());
        this.radius = radius;
        focusPoint = new Point2D.Float((float) focal.getX(), (float) focal.getY());
    }
    
    /// Creates an sRGB radial gradient with explicit spread behavior and a centered focus.
    public RadialGradientPaint(Point2D center, float radius, float[] stops, Color[] colors, short spreadMethod, boolean adapting) {
        this(center, radius, stops, colors, center, spreadMethod, adapting);
    }
    
    /// Creates a deep copy of another radial gradient paint.
    public RadialGradientPaint(RadialGradientPaint paint) {
        this(paint.getCenter(), paint.getRadius(), paint.getStops(), paint.getColors(), paint.getFocal(), paint.getSpreadMethod(),
                paint.getColorSpace(), paint.getTransform(), paint.isAdapting());
    }
    
    /// Applies adaptive bounds and the optional extra transform before creating the concrete
    /// radial-gradient context.
    ///
    /// If the resulting transform cannot be inverted, the paint falls back to a solid context
    /// built from the midpoint color rather than failing the render.
    @Override
    public PaintContext createContext(ColorModel colorModel, Rectangle deviceBounds, Rectangle2D userBounds, AffineTransform transform,
            RenderingHints hints) {
        Rectangle2D paintBounds = userBounds;
        AffineTransform paintTransform = transform;
        Rectangle2D adaptableBounds = (Rectangle2D) hints.get(AdaptablePaint.KEY_USER_BOUNDS);
        if (adaptableBounds != null)
            paintBounds = adaptableBounds;
        block: {
            if (!super.adapting)
                if (super.transform == null)
                    break block;
            paintTransform = new AffineTransform(paintTransform);
        } // end block
        
        if (super.adapting) {
            paintTransform.translate(paintBounds.getX(), paintBounds.getY());
            paintTransform.scale(paintBounds.getWidth(), paintBounds.getHeight());
        }
        if (super.transform != null)
            paintTransform.concatenate(super.transform);
        RadialGradientPaintContext context;
        try {
            context = new RadialGradientPaintContext(colorModel, deviceBounds, paintBounds, paintTransform, hints, centerPoint.x, centerPoint.y, radius,
                    focusPoint.x, focusPoint.y, super.stops, super.colors, super.spreadMethod, super.colorSpace);
        } catch (NoninvertibleTransformException e1) {
            return super.interpolateColor(0.5f).createContext(colorModel, deviceBounds, paintBounds, paintTransform, hints);
        }
        return context;
    }
    
    /// Returns a defensive copy of the gradient center point.
    public Point2D getCenter() {
        return (Point2D) centerPoint.clone();
    }
    
    /// Returns a defensive copy of the focal point.
    public Point2D getFocal() {
        return (Point2D) focusPoint.clone();
    }
    
    /// Returns the circle radius used by the gradient.
    public float getRadius() {
        return radius;
    }
}
