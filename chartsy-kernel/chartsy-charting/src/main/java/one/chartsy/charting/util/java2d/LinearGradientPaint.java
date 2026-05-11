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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/// Gradient paint that interpolates colors along a straight line segment.
///
/// The line runs from [#getStart()] to [#getEnd()]. When [#isAdapting()] is enabled, those points
/// are interpreted relative to the current user bounds supplied through
/// [AdaptablePaint#KEY_USER_BOUNDS], which lets callers define gradients once in normalized
/// `0..1` coordinates and reuse them for differently sized chart elements.
///
/// The supplied endpoint objects are copied on construction and the accessors return defensive
/// copies, so later caller-side mutations do not affect the paint.
public class LinearGradientPaint extends MultipleGradientPaint {
    private transient Point2D.Float startPoint;
    private transient Point2D.Float endPoint;
    
    /// Creates an sRGB linear gradient from raw coordinate values.
    public LinearGradientPaint(float startX, float startY, float endX, float endY, float[] stops, Color[] colors, boolean adapting) {
        this(startX, startY, endX, endY, stops, colors, (short) 1, adapting);
    }
    
    /// Creates an sRGB linear gradient from raw coordinate values with an explicit spread mode.
    public LinearGradientPaint(float startX, float startY, float endX, float endY, float[] stops, Color[] colors, short spreadMethod,
            boolean adapting) {
        this(new Point2D.Float(startX, startY), new Point2D.Float(endX, endY), stops, colors, spreadMethod, adapting);
    }
    
    /// Creates a deep copy of another linear gradient paint.
    public LinearGradientPaint(LinearGradientPaint paint) {
        this(paint.getStart(), paint.getEnd(), paint.getStops(), paint.getColors(), paint.getSpreadMethod(), paint.getColorSpace(),
                paint.getTransform(), paint.isAdapting());
    }
    
    /// Creates an sRGB linear gradient between two points.
    public LinearGradientPaint(Point2D start, Point2D end, float[] stops, Color[] colors, boolean adapting) {
        this(start, end, stops, colors, (short) 1, adapting);
    }
    
    /// Creates an sRGB linear gradient with an explicit spread mode.
    public LinearGradientPaint(Point2D start, Point2D end, float[] stops, Color[] colors, short spreadMethod, boolean adapting) {
        this(start, end, stops, colors, spreadMethod, (short) 0, null, adapting);
    }
    
    /// Creates a linear gradient between two distinct points.
    ///
    /// The gradient definition itself is immutable after construction. If `adapting` is `true`,
    /// the coordinates are treated as relative to the user bounds available at paint time and
    /// `transform` is applied after that adaptive normalization.
    ///
    /// @param spreadMethod one of the `SPREAD_*` constants from [MultipleGradientPaintConstants]
    /// @param colorSpace one of [#SRGB] or [#LINEAR_RGB]
    /// @param transform optional extra transform applied after adaptive user-bounds scaling
    /// @throws IllegalArgumentException if either endpoint is `null` or both describe the same
    ///                                  location
    public LinearGradientPaint(Point2D start, Point2D end, float[] stops, Color[] colors, short spreadMethod, short colorSpace,
            AffineTransform transform, boolean adapting) {
        super(stops, colors, spreadMethod, colorSpace, transform, adapting);
        if (start != null)
            if (end != null)
                if (!start.equals(end)) {
                    startPoint = new Point2D.Float((float) start.getX(), (float) start.getY());
                    endPoint = new Point2D.Float((float) end.getX(), (float) end.getY());
                    return;
                }
        throw new IllegalArgumentException("Invalid start and/or end point: " + start +
                " " + end);
    }
    
    /// Applies adaptive bounds and the optional extra transform before creating the concrete
    /// paint context.
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
        LinearGradientPaintContext context;
        try {
            context = new LinearGradientPaintContext(colorModel, deviceBounds, paintBounds, paintTransform, hints, startPoint, endPoint,
                    super.stops, super.colors, super.spreadMethod, super.colorSpace);
        } catch (NoninvertibleTransformException e1) {
            return super.interpolateColor(0.5f).createContext(colorModel, deviceBounds, paintBounds, paintTransform, hints);
        }
        return context;
    }
    
    /// Returns a defensive copy of the gradient's end point.
    public Point2D getEnd() {
        return (Point2D) endPoint.clone();
    }
    
    /// Returns a defensive copy of the gradient's start point.
    public Point2D getStart() {
        return (Point2D) startPoint.clone();
    }
    
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        boolean hasStartPoint = in.readBoolean();
        float pointY;
        if (hasStartPoint) {
            float pointX = in.readFloat();
            pointY = in.readFloat();
            startPoint = new Point2D.Float(pointX, pointY);
        }
        boolean hasEndPoint = in.readBoolean();
        if (hasEndPoint) {
            float pointX = in.readFloat();
            pointY = in.readFloat();
            endPoint = new Point2D.Float(pointX, pointY);
        }
    }
    
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        boolean hasStartPoint = startPoint != null;
        out.writeBoolean(hasStartPoint);
        if (hasStartPoint) {
            out.writeFloat(startPoint.x);
            out.writeFloat(startPoint.y);
        }
        boolean hasEndPoint = endPoint != null;
        out.writeBoolean(hasEndPoint);
        if (hasEndPoint) {
            out.writeFloat(endPoint.x);
            out.writeFloat(endPoint.y);
        }
    }
}
