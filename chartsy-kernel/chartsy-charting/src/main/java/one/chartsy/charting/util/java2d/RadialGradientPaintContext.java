package one.chartsy.charting.util.java2d;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;

/// Rasterizer for [RadialGradientPaint].
///
/// The context supports two rendering paths. When the focus coincides with the center, the spread
/// mode is pad, and the shared gradient lookup is simple, it can map squared distances through a
/// precomputed square-root table. All other configurations use the general focus-aware algorithm
/// that intersects the focus-to-pixel ray with the enclosing circle before indexing the shared
/// gradient tables.
final class RadialGradientPaintContext extends MultipleGradientPaintContext {
    private static final int SQRT_LOOKUP_SIZE = 256;
    private static final float FOCUS_CLAMP_FACTOR = 0.97f;
    
    private final boolean useSimpleFocusLookup;
    private final float radiusSq;
    private final float centerXBias;
    private final float centerYBias;
    private final float focusVerticalLimit;
    private final float centerX;
    private final float centerY;
    private final float focusX;
    private final float focusY;
    private final float radius;
    private float sqrtLookupScale;
    private final int[] sqrtLookup = new int[SQRT_LOOKUP_SIZE];
    
    /// Precomputes radial-gradient lookup state for the supplied center, radius, and focus point.
    ///
    /// When the focus matches the center, the spread mode is pad, and the shared gradient lookup
    /// is already flattened into a single array, this context enables the finite-difference fast
    /// path backed by [#sqrtLookup].
    ///
    /// ### Implementation Note
    ///
    /// If the requested focus lies outside the circle, the current implementation moves it back
    /// inside the circle before rendering. That keeps the ray-circle intersection math used by
    /// the general rasterizer numerically well-defined without rejecting the paint request.
    ///
    /// @throws NoninvertibleTransformException if the shared paint transform cannot be inverted
    public RadialGradientPaintContext(ColorModel colorModel, Rectangle deviceBounds, Rectangle2D userBounds, AffineTransform transform,
            RenderingHints hints, float centerX, float centerY, float radius, float focusX, float focusY, float[] fractions, Color[] colors,
            short spreadMethod, short colorSpace) throws NoninvertibleTransformException {
        super(colorModel, deviceBounds, userBounds, transform, hints, fractions, colors, spreadMethod, colorSpace);
        super.calculateGradientFractions();
        this.useSimpleFocusLookup = super._isSimpleLookup
                && focusX == centerX
                && focusY == centerY
                && spreadMethod == SPREAD_PAD;
        float effectiveFocusX = focusX;
        float effectiveFocusY = focusY;
        float radiusSq = radius * radius;

        float focusOffsetX = effectiveFocusX - centerX;
        float focusOffsetY = effectiveFocusY - centerY;
        double focusDistance = Math.sqrt(focusOffsetX * focusOffsetX + focusOffsetY * focusOffsetY);
        if (focusDistance > radius) {
            // Preserve the historical horizontal special case instead of shifting existing output.
            if (focusOffsetY == 0.0f) {
                effectiveFocusY = centerY;
                effectiveFocusX = centerX + radius * FOCUS_CLAMP_FACTOR;
            } else {
                double focusAngle = Math.atan2(focusOffsetY, focusOffsetX);
                effectiveFocusX = centerX + (float) (FOCUS_CLAMP_FACTOR * radius * Math.cos(focusAngle));
                effectiveFocusY = centerY + (float) (FOCUS_CLAMP_FACTOR * radius * Math.sin(focusAngle));
            }
        }
        focusOffsetX = effectiveFocusX - centerX;
        this.radiusSq = radiusSq;
        this.centerX = centerX;
        this.centerY = centerY;
        this.focusX = effectiveFocusX;
        this.focusY = effectiveFocusY;
        this.radius = radius;
        focusVerticalLimit = (float) Math.sqrt(radiusSq - focusOffsetX * focusOffsetX);
        centerXBias = super._a02 - centerX;
        centerYBias = super._a12 - centerY;
        if (this.useSimpleFocusLookup)
            buildSqrtLookup();
    }
    
    /// Builds the square-root lookup table used by the centered fast path.
    private void buildSqrtLookup() {
        sqrtLookupScale = super._fastGradientArraySize * super._fastGradientArraySize / (float) (SQRT_LOOKUP_SIZE - 2);
        for (int lookupIndex = 0; lookupIndex < SQRT_LOOKUP_SIZE - 1; lookupIndex++)
            sqrtLookup[lookupIndex] = (int) Math.sqrt(lookupIndex * sqrtLookupScale);
        sqrtLookup[SQRT_LOOKUP_SIZE - 1] = sqrtLookup[SQRT_LOOKUP_SIZE - 2];
    }
    
    /// Fills a raster tile using the centered-focus fast path.
    ///
    /// The implementation updates squared distances with finite differences so it can avoid a
    /// square-root calculation for every pixel.
    private void fillSimpleFocusRaster(int[] pixels, int off, int adjust, int x, int y, int w, int h) {
        float gradientScale = super._fastGradientArraySize / radius;
        float rowStartX = super._a00 * x + super._a01 * y + centerXBias;
        float rowStartY = super._a10 * x + super._a11 * y + centerYBias;
        float xStepX = gradientScale * super._a00;
        float xStepY = gradientScale * super._a10;
        int pixelIndex = off;
        int rowAdvance = w + adjust;
        int maxDistanceSqScaled = super._fastGradientArraySize * super._fastGradientArraySize << 4;

        for (int row = 0; row < h; row++) {
            float scaledX = gradientScale * (super._a01 * row + rowStartX);
            float scaledY = gradientScale * (super._a11 * row + rowStartY);
            float squaredDistanceDelta = xStepX * xStepX + xStepY * xStepY;
            int distanceSqScaled = (int) ((scaledX * scaledX + scaledY * scaledY) * 16.0f);
            int distanceStepScaled = (int) (((xStepX * scaledX + xStepY * scaledY) * 2.0f + squaredDistanceDelta) * 16.0f);
            int distanceStepIncrementScaled = (int) (squaredDistanceDelta * 32.0f);

            for (int column = 0; column < w; column++) {
                int gradientIndex;
                if (distanceSqScaled > maxDistanceSqScaled)
                    gradientIndex = super._fastGradientArraySize;
                else if (distanceSqScaled < 0)
                    gradientIndex = 0;
                else {
                    float lookupPosition = (distanceSqScaled >>> 4) / sqrtLookupScale;
                    int lookupIndex = (int) lookupPosition;
                    float lookupFraction = lookupPosition - lookupIndex;
                    gradientIndex = (int) (lookupFraction * sqrtLookup[lookupIndex + 1]
                            + (1.0f - lookupFraction) * sqrtLookup[lookupIndex]);
                }
                pixels[pixelIndex + column] = super._gradient[gradientIndex];
                distanceSqScaled += distanceStepScaled;
                distanceStepScaled += distanceStepIncrementScaled;
            }
            pixelIndex += rowAdvance;
        }
    }
    
    /// Fills a raster tile using the general focus-aware ray-circle intersection path.
    private void fillGeneralRaster(int[] pixels, int off, int adjust, int x, int y, int w, int h) {
        double circleConstant = centerX * centerX + centerY * centerY - radiusSq;
        float rowStartX = super._a00 * x + super._a01 * y + super._a02;
        float rowStartY = super._a10 * x + super._a11 * y + super._a12;
        double twiceCenterY = 2.0f * centerY;
        double minusTwiceCenterX = -2.0f * centerX;
        int pixelIndex = off;
        int rowAdvance = w + adjust;

        for (int row = 0; row < h; row++) {
            float currentX = super._a01 * row + rowStartX;
            float currentY = super._a11 * row + rowStartY;

            for (int column = 0; column < w; column++) {
                double boundaryX;
                double boundaryY;
                if (currentX == focusX) {
                    boundaryX = focusX;
                    boundaryY = centerY + ((currentY > focusY) ? focusVerticalLimit : -focusVerticalLimit);
                } else {
                    double slope = (currentY - focusY) / (currentX - focusX);
                    double intercept = currentY - slope * currentX;
                    double quadraticA = slope * slope + 1.0;
                    double quadraticB = minusTwiceCenterX - 2.0 * slope * (centerY - intercept);
                    double quadraticC = circleConstant + intercept * (intercept - twiceCenterY);
                    double discriminantRoot = Math.sqrt(quadraticB * quadraticB - 4.0 * quadraticA * quadraticC);
                    boundaryX = (-quadraticB + ((currentX >= focusX) ? discriminantRoot : -discriminantRoot)) / (2.0 * quadraticA);
                    boundaryY = slope * boundaryX + intercept;
                }

                float pixelOffsetX = currentX - focusX;
                float pixelOffsetY = currentY - focusY;
                float pixelDistanceSq = pixelOffsetX * pixelOffsetX + pixelOffsetY * pixelOffsetY;
                float boundaryOffsetX = (float) boundaryX - focusX;
                float boundaryOffsetY = (float) boundaryY - focusY;
                float boundaryDistanceSq = boundaryOffsetX * boundaryOffsetX + boundaryOffsetY * boundaryOffsetY;
                float gradientPosition = (float) Math.sqrt(pixelDistanceSq / boundaryDistanceSq);
                pixels[pixelIndex + column] = super.indexIntoGradientsArrays(gradientPosition);
                currentX += super._a00;
                currentY += super._a10;
            }
            pixelIndex += rowAdvance;
        }
    }
    
    /// Dispatches to the centered fast path or the general focus-aware rasterizer.
    @Override
    protected void fillRaster(int[] pixels, int off, int adjust, int x, int y, int w, int h) {
        if (!useSimpleFocusLookup)
            fillGeneralRaster(pixels, off, adjust, x, y, w, h);
        else
            fillSimpleFocusRaster(pixels, off, adjust, x, y, w, h);
    }
}
