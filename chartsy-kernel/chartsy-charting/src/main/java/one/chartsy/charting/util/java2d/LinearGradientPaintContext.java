package one.chartsy.charting.util.java2d;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;

/// Rasterizer for [LinearGradientPaint].
///
/// The shared gradient lookup data comes from [MultipleGradientPaintContext]. This subclass only
/// needs to project the configured start-to-end vector into device space and then advance that
/// projection by a constant amount per pixel while filling the tile.
class LinearGradientPaintContext extends MultipleGradientPaintContext {
    private final float gradientDeltaX;
    private final float gradientDeltaY;
    private final float gradientOffset;
    
    /// Precomputes the affine coefficients that map a device-space pixel to its normalized
    /// position on the gradient axis.
    public LinearGradientPaintContext(ColorModel colorModel, Rectangle deviceBounds, Rectangle2D userBounds, AffineTransform transform,
            RenderingHints hints, Point2D.Float start, Point2D.Float end, float[] fractions, Color[] colors, short spreadMethod,
            short colorSpace) throws NoninvertibleTransformException {
        super(colorModel, deviceBounds, userBounds, transform, hints, fractions, colors, spreadMethod, colorSpace);
        super.calculateGradientFractions();
        float deltaX = end.x - start.x;
        float deltaY = end.y - start.y;
        float axisLengthSquared = deltaX * deltaX + deltaY * deltaY;
        float normalizedAxisX = deltaX / axisLengthSquared;
        float normalizedAxisY = deltaY / axisLengthSquared;
        gradientDeltaX = super._a00 * normalizedAxisX + super._a10 * normalizedAxisY;
        gradientDeltaY = super._a01 * normalizedAxisX + super._a11 * normalizedAxisY;
        gradientOffset = (super._a02 - start.x) * normalizedAxisX + (super._a12 - start.y) * normalizedAxisY;
    }
    
    @Override
    protected void fillRaster(int[] pixels, int off, int adjust, int x, int y, int w, int h) {
        int pixelIndex = off;
        float rowOffset = gradientDeltaX * x + gradientOffset;
        int row = 0;
        while (true) {
            if (row >= h)
                break;
            float gradientPosition = rowOffset + gradientDeltaY * y;
            y++;
            int rowEnd = pixelIndex + w;
            while (true) {
                if (pixelIndex >= rowEnd)
                    break;
                pixels[pixelIndex++] = super.indexIntoGradientsArrays(gradientPosition);
                gradientPosition += gradientDeltaX;
            }
            pixelIndex += adjust;
            row++;
        }
    }
}
