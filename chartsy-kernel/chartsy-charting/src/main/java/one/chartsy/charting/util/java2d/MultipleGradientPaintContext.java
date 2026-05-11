package one.chartsy.charting.util.java2d;

import java.awt.Color;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;
import java.lang.ref.WeakReference;

/// Shared [PaintContext] base for Chartsy's multi-stop gradient paints.
///
/// The context normalizes caller-provided fractions so the gradient always spans the full
/// `[0, 1]` interval, precomputes the inverse paint transform used by concrete geometries,
/// builds cached lookup tables for stop interpolation, and reuses compatible rasters across
/// paint requests.
///
/// Subclasses are responsible only for the geometry-specific step of mapping each device pixel
/// into a normalized gradient position and writing colors through
/// [fillRaster(int[], int, int, int, int, int, int)].
abstract class MultipleGradientPaintContext implements PaintContext {
    public static final short SPREAD_PAD = 1;
    public static final short SPREAD_REFLECT = 2;
    public static final short SPREAD_REPEAT = 3;
    private static final ColorModel OPAQUE_SRGB_MODEL = new DirectColorModel(ColorSpace.getInstance(1004), 24, 0xff0000, 0xff00, 255, 0,
            false, 3);
    private static final ColorModel OPAQUE_LINEAR_RGB_MODEL = new DirectColorModel(ColorSpace.getInstance(1000), 24, 0xff0000, 0xff00, 255, 0,
            false, 3);
    private static final ColorModel TRANSLUCENT_SRGB_MODEL = new DirectColorModel(ColorSpace.getInstance(1004), 32, 0xff0000, 0xff00, 255,
            0xff000000, false, 3);
    private static final ColorModel TRANSLUCENT_LINEAR_RGB_MODEL = new DirectColorModel(ColorSpace.getInstance(1000), 32, 0xff0000, 0xff00, 255,
            0xff000000, false, 3);
    protected static ColorModel _cachedModel;
    protected static WeakReference<Raster> _cached;
    protected static final int GRADIENT_SIZE_INDEX = 255;
    
    /// Reuses the most recently released raster when it matches the color model and requested
    /// size, otherwise creates a new compatible raster.
    protected static final synchronized Raster getCachedRaster(ColorModel colorModel, int width, int height) {
        if (colorModel == MultipleGradientPaintContext._cachedModel && MultipleGradientPaintContext._cached != null) {
            Raster cachedRaster = MultipleGradientPaintContext._cached.get();
            if (cachedRaster != null && cachedRaster.getWidth() >= width && cachedRaster.getHeight() >= height) {
                MultipleGradientPaintContext._cached = null;
                return cachedRaster;
            }
        }
        return colorModel.createCompatibleWritableRaster(width, height);
    }
    
    /// Stores a raster for later reuse when it is larger than the current cached candidate.
    protected static final synchronized void putCachedRaster(ColorModel colorModel, Raster raster) {
        if (MultipleGradientPaintContext._cached != null) {
            Raster cachedRaster = MultipleGradientPaintContext._cached.get();
            if (cachedRaster != null) {
                int cachedWidth = cachedRaster.getWidth();
                int cachedHeight = cachedRaster.getHeight();
                int rasterWidth = raster.getWidth();
                int rasterHeight = raster.getHeight();
                if (cachedWidth >= rasterWidth && cachedHeight >= rasterHeight)
                    return;
                if (cachedWidth * cachedHeight >= rasterWidth * rasterHeight)
                    return;
            }
        }
        MultipleGradientPaintContext._cachedModel = colorModel;
        MultipleGradientPaintContext._cached = new WeakReference<>(raster);
    }
    
    protected ColorModel _model;
    protected Raster _saved;
    protected short _spreadMethod;
    protected short _colorSpace;
    protected float _a00;
    protected float _a01;
    protected float _a10;
    protected float _a11;
    protected float _a02;
    protected float _a12;
    protected boolean _isSimpleLookup;
    protected int _fastGradientArraySize;
    protected int[] _gradient;
    private int[][] gradients;
    private int lastGradientColor;
    private int gradientSegmentCount;
    protected float[] _normalizedIntervals;
    protected float[] _fractions;
    
    private final Color[] gradientColors;
    
    private int combinedColorMask;
    
    /// Creates a context for a gradient defined by paired fractions and colors.
    ///
    /// Missing endpoint stops at `0` or `1` are synthesized by duplicating the nearest supplied
    /// color so downstream lookup generation can treat every gradient as a closed normalized
    /// interval.
    public MultipleGradientPaintContext(ColorModel colorModel, Rectangle deviceBounds, Rectangle2D userBounds, AffineTransform transform,
            RenderingHints hints, float[] fractions, Color[] colors, short spreadMethod, short colorSpace) throws NoninvertibleTransformException {
        _isSimpleLookup = true;
        boolean needsStartStop = fractions[0] != 0.0f;
        boolean needsEndStop = fractions[fractions.length - 1] != 1.0f;
        block: {
            if (needsStartStop)
                if (needsEndStop) {
                    _fractions = new float[fractions.length + 2];
                    System.arraycopy(fractions, 0, _fractions, 1, fractions.length);
                    _fractions[0] = 0.0f;
                    _fractions[_fractions.length - 1] = 1.0f;
                    gradientColors = new Color[colors.length + 2];
                    System.arraycopy(colors, 0, gradientColors, 1, colors.length);
                    gradientColors[0] = colors[0];
                    gradientColors[gradientColors.length - 1] = colors[colors.length - 1];
                    break block;
                }
            if (needsStartStop) {
                _fractions = new float[fractions.length + 1];
                System.arraycopy(fractions, 0, _fractions, 1, fractions.length);
                _fractions[0] = 0.0f;
                gradientColors = new Color[colors.length + 1];
                System.arraycopy(colors, 0, gradientColors, 1, colors.length);
                gradientColors[0] = colors[0];
            } else if (!needsEndStop) {
                _fractions = new float[fractions.length];
                System.arraycopy(fractions, 0, _fractions, 0, fractions.length);
                gradientColors = new Color[colors.length];
                System.arraycopy(colors, 0, gradientColors, 0, colors.length);
            } else {
                _fractions = new float[fractions.length + 1];
                System.arraycopy(fractions, 0, _fractions, 0, fractions.length);
                _fractions[_fractions.length - 1] = 1.0f;
                gradientColors = new Color[colors.length + 1];
                System.arraycopy(colors, 0, gradientColors, 0, colors.length);
                gradientColors[gradientColors.length - 1] = colors[colors.length - 1];
            }
        } // end block
        
        _normalizedIntervals = new float[_fractions.length - 1];
        float currentFraction = _fractions[0];
        float previousFraction = 0.0f;
        int fractionIndex = 1;
        while (true) {
            if (fractionIndex >= _fractions.length)
                break;
            previousFraction = currentFraction;
            currentFraction = _fractions[fractionIndex];
            _normalizedIntervals[fractionIndex - 1] = currentFraction - previousFraction;
            fractionIndex++;
        }
        AffineTransform inverseTransform = transform.createInverse();
        double[] matrix = new double[6];
        inverseTransform.getMatrix(matrix);
        _a00 = (float) matrix[0];
        _a10 = (float) matrix[1];
        _a01 = (float) matrix[2];
        _a11 = (float) matrix[3];
        _a02 = (float) matrix[4];
        _a12 = (float) matrix[5];
        _spreadMethod = spreadMethod;
        _colorSpace = colorSpace;
        if (colorModel.getColorSpace() == MultipleGradientPaintContext.TRANSLUCENT_SRGB_MODEL.getColorSpace())
            _model = MultipleGradientPaintContext.TRANSLUCENT_SRGB_MODEL;
        else if (colorModel.getColorSpace() == MultipleGradientPaintContext.TRANSLUCENT_LINEAR_RGB_MODEL.getColorSpace())
            _model = MultipleGradientPaintContext.TRANSLUCENT_LINEAR_RGB_MODEL;
        else
            throw new IllegalArgumentException("Unsupported ColorSpace for interpolation");
    }
    
    private void buildSegmentGradients() {
        _isSimpleLookup = false;
        int gradientIndex = 0;
        while (true) {
            if (gradientIndex >= gradients.length)
                break;
            gradients[gradientIndex] = new int[256];
            int startColor = gradientColors[gradientIndex].getRGB();
            int endColor = gradientColors[gradientIndex + 1].getRGB();
            GradientUtil.interpolate(startColor, endColor, gradients[gradientIndex]);
            combinedColorMask &= startColor;
            combinedColorMask &= endColor;
            gradientIndex++;
        }
        int colorIndex;
        if (_colorSpace != 1) {
            if (_model.getColorSpace() == ColorSpace.getInstance(1004)) {
                gradientIndex = 0;
                while (true) {
                    if (gradientIndex >= gradients.length)
                        break;
                    colorIndex = 0;
                    while (true) {
                        if (colorIndex >= gradients[gradientIndex].length)
                            break;
                        gradients[gradientIndex][colorIndex] = GradientUtil.convertEntireColorSRGBtoLinearRGB(gradients[gradientIndex][colorIndex]);
                        colorIndex++;
                    }
                    gradientIndex++;
                }
            }
        } else if (_model.getColorSpace() == ColorSpace.getInstance(1000)) {
            gradientIndex = 0;
            while (true) {
                if (gradientIndex >= gradients.length)
                    break;
                colorIndex = 0;
                while (true) {
                    if (colorIndex >= gradients[gradientIndex].length)
                        break;
                    gradients[gradientIndex][colorIndex] = GradientUtil.convertEntireColorLinearRGBtoSRGB(gradients[gradientIndex][colorIndex]);
                    colorIndex++;
                }
                gradientIndex++;
            }
        }
        lastGradientColor = gradients[gradients.length - 1][255];
    }
    
    private void buildSingleGradientLookup(float smallestInterval) {
        _isSimpleLookup = true;
        int gradientSize = 1;
        int gradientIndex = 0;
        int segmentIndex;
        while (true) {
            if (gradientIndex >= gradients.length)
                break;
            segmentIndex = (int) (_normalizedIntervals[gradientIndex] / smallestInterval * 255.0f);
            gradientSize += segmentIndex;
            gradients[gradientIndex] = new int[segmentIndex];
            int startColor = gradientColors[gradientIndex].getRGB();
            int endColor = gradientColors[gradientIndex + 1].getRGB();
            GradientUtil.interpolate(startColor, endColor, gradients[gradientIndex]);
            combinedColorMask &= startColor;
            combinedColorMask &= endColor;
            gradientIndex++;
        }
        _gradient = new int[gradientSize];
        gradientIndex = 0;
        segmentIndex = 0;
        while (true) {
            if (segmentIndex >= gradients.length)
                break;
            System.arraycopy(gradients[segmentIndex], 0, _gradient, gradientIndex, gradients[segmentIndex].length);
            gradientIndex += gradients[segmentIndex].length;
            segmentIndex++;
        }
        _gradient[_gradient.length - 1] = gradientColors[gradientColors.length - 1].getRGB();
        if (_colorSpace != 1) {
            if (_model.getColorSpace() == ColorSpace.getInstance(1004)) {
                segmentIndex = 0;
                while (true) {
                    if (segmentIndex >= _gradient.length)
                        break;
                    _gradient[segmentIndex] = GradientUtil.convertEntireColorSRGBtoLinearRGB(_gradient[segmentIndex]);
                    segmentIndex++;
                }
            }
        } else if (_model.getColorSpace() == ColorSpace.getInstance(1000)) {
            segmentIndex = 0;
            while (true) {
                if (segmentIndex >= _gradient.length)
                    break;
                _gradient[segmentIndex] = GradientUtil.convertEntireColorLinearRGBtoSRGB(_gradient[segmentIndex]);
                segmentIndex++;
            }
        }
        _fastGradientArraySize = _gradient.length - 1;
    }
    
    /// Builds the color lookup data used by `indexIntoGradientsArrays(float)`.
    ///
    /// Small gradients are flattened into one contiguous lookup table for direct indexing.
    /// Larger gradients keep one lookup table per stop interval to avoid allocating a single
    /// very large array for extremely uneven stop spacing.
    protected final void calculateGradientFractions() {
        int colorIndex;
        if (_colorSpace == 1) {
            colorIndex = 0;
            while (true) {
                if (colorIndex >= gradientColors.length)
                    break;
                gradientColors[colorIndex] = new Color(GradientUtil.SRGB_TO_LINEAR_RGB[gradientColors[colorIndex].getRed()],
                        GradientUtil.SRGB_TO_LINEAR_RGB[gradientColors[colorIndex].getGreen()], GradientUtil.SRGB_TO_LINEAR_RGB[gradientColors[colorIndex].getBlue()]);
                colorIndex++;
            }
        }
        combinedColorMask = 0xff000000;
        gradients = new int[_fractions.length - 1][];
        gradientSegmentCount = gradients.length;
        int intervalCount = _normalizedIntervals.length;
        float smallestInterval = 1.0f;
        int intervalIndex = 0;
        while (true) {
            if (intervalIndex >= intervalCount)
                break;
            smallestInterval = Math.min(smallestInterval, _normalizedIntervals[intervalIndex]);
            if (smallestInterval == 0.0f)
                break;
            intervalIndex++;
        }
        int estimatedGradientSize = 0;
        if (smallestInterval == 0.0f)
            estimatedGradientSize = Integer.MAX_VALUE;
        else {
            int estimateIndex = 0;
            while (true) {
                if (estimateIndex >= _normalizedIntervals.length)
                    break;
                estimatedGradientSize = (int) (estimatedGradientSize + _normalizedIntervals[estimateIndex] / smallestInterval * 256.0f);
                estimateIndex++;
            }
        }
        if (estimatedGradientSize > 5000)
            this.buildSegmentGradients();
        else
            this.buildSingleGradientLookup(smallestInterval);
        if (combinedColorMask >>> 24 == 255)
            if (_model.getColorSpace() == MultipleGradientPaintContext.OPAQUE_SRGB_MODEL.getColorSpace())
                _model = MultipleGradientPaintContext.OPAQUE_SRGB_MODEL;
            else if (_model.getColorSpace() == MultipleGradientPaintContext.OPAQUE_LINEAR_RGB_MODEL.getColorSpace())
                _model = MultipleGradientPaintContext.OPAQUE_LINEAR_RGB_MODEL;
    }
    
    @Override
    public final void dispose() {
        if (_saved != null) {
            MultipleGradientPaintContext.putCachedRaster(_model, _saved);
            _saved = null;
        }
    }
    
    /// Fills one raster tile with gradient colors.
    ///
    /// Implementation requirements:
    /// - `pixels` addresses the tile's backing `int` buffer.
    /// - `off` is the first writable index for the tile.
    /// - `adjust` must be added after each row to advance from the written span to the next
    ///   scanline start.
    /// - `x`, `y`, `w`, and `h` describe the requested device-space tile.
    ///
    /// Implementations should convert device coordinates into normalized gradient positions and
    /// obtain colors through `indexIntoGradientsArrays(float)` or the precomputed `_gradient`
    /// lookup data.
    protected abstract void fillRaster(int[] pixels, int off, int adjust, int x, int y, int w, int h);
    
    @Override
    public final ColorModel getColorModel() {
        return _model;
    }
    
    /// Obtains a writable raster for the requested tile and delegates pixel generation to the
    /// geometry-specific subclass.
    @Override
    public final Raster getRaster(int x, int y, int w, int h) {
        Raster raster = _saved;
        block: {
            if (raster != null)
                if (raster.getWidth() >= w)
                    if (raster.getHeight() >= h)
                        break block;
            raster = MultipleGradientPaintContext.getCachedRaster(_model, w, h);
            _saved = raster;
        } // end block
        
        DataBufferInt buffer = (DataBufferInt) raster.getDataBuffer();
        int[] pixels = buffer.getBankData()[0];
        int off = buffer.getOffset();
        int scanlineStride = ((SinglePixelPackedSampleModel) raster.getSampleModel()).getScanlineStride();
        int adjust = scanlineStride - w;
        fillRaster(pixels, off, adjust, x, y, w, h);
        return raster;
    }
    
    /// Resolves a normalized position into an ARGB color after applying the configured spread
    /// mode.
    protected final int indexIntoGradientsArrays(float position) {
        float gradientPosition = position;
        int gradientIndex;
        block: if (_spreadMethod != 1) {
            if (_spreadMethod == 3) {
                gradientPosition = gradientPosition - (int) gradientPosition;
                if (gradientPosition <= 1.0f)
                    if (gradientPosition >= -1.0f) {
                        if (gradientPosition >= 0.0f)
                            break block;
                        gradientPosition = gradientPosition + 1.0f;
                        break block;
                    }
                gradientPosition = 0.0f;
            } else {
                if (gradientPosition < 0.0f)
                    gradientPosition = -gradientPosition;
                gradientIndex = (int) gradientPosition;
                gradientPosition = gradientPosition - gradientIndex;
                if (gradientPosition > 1.0f)
                    gradientPosition = 0.0f;
                else if ((gradientIndex & 1) != 0)
                    gradientPosition = 1.0f - gradientPosition;
            }
        } else if (gradientPosition > 1.0f)
            gradientPosition = 1.0f;
        else if (gradientPosition < 0.0f)
            gradientPosition = 0.0f;
        
        if (_isSimpleLookup)
            return _gradient[(int) (gradientPosition * _fastGradientArraySize)];
        gradientIndex = 0;
        while (true) {
            if (gradientIndex >= gradientSegmentCount)
                return lastGradientColor;
            if (gradientPosition < _fractions[gradientIndex + 1])
                break;
            gradientIndex++;
        }
        float intervalPosition = gradientPosition - _fractions[gradientIndex];
        int colorIndex = (int) (intervalPosition / _normalizedIntervals[gradientIndex] * 255.0f);
        return gradients[gradientIndex][colorIndex];
    }
}
