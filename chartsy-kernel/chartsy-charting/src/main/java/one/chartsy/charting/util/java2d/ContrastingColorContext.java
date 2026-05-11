package one.chartsy.charting.util.java2d;

import java.awt.CompositeContext;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/// Implements the destination-sensitive composite used by [ContrastingColor].
///
/// `LabelRenderer` reaches this context when a contrasting foreground cannot be resolved eagerly
/// from a known background color. Instead of blending source and destination samples, the context
/// inspects the existing destination/background pixel from `dstIn` and writes either the configured
/// dark or bright replacement color into `dstOut`.
///
/// The source raster contributes only the span to process. Source pixel values and alpha are
/// ignored, which matches the way `LabelRenderer` uses this composite as a late background probe
/// for monochrome glyph rendering.
///
/// The constructor precomputes destination-model encodings for the dark and bright fallback
/// colors. [#compose(Raster, Raster, WritableRaster)] then dispatches to packed, component, or
/// fully generic loops based on the destination [ColorModel].
class ContrastingColorContext implements CompositeContext {
    private static final int WHITE_RGB = 0x00ff_ffff;
    private static final float RED_BRIGHTNESS_WEIGHT = 0.299f;
    private static final float GREEN_BRIGHTNESS_WEIGHT = 0.587f;
    private static final float BLUE_BRIGHTNESS_WEIGHT = 0.114f;

    private final Object darkDataElements;
    private final Object brightDataElements;
    private final int darkTransferValue;
    private final int brightTransferValue;
    private final ColorModel colorModel;
    private final ContrastingColor contrastColor;

    /// Creates a context that writes contrast colors encoded for `colorModel`.
    ContrastingColorContext(ContrastingColor contrastColor, ColorModel colorModel) {
        this.contrastColor = contrastColor;
        this.colorModel = colorModel;
        darkDataElements = colorModel.getDataElements(contrastColor.getDarkColor().getRGB(), null);
        brightDataElements = colorModel.getDataElements(contrastColor.getBrightColor().getRGB(), null);
        darkTransferValue = computeTransferValue(darkDataElements);
        brightTransferValue = computeTransferValue(brightDataElements);
    }

    private int composeWidth(Raster src, Raster dstIn, WritableRaster dstOut) {
        return Math.min(src.getWidth(), Math.min(dstIn.getWidth(), dstOut.getWidth()));
    }

    private int composeHeight(Raster src, Raster dstIn, WritableRaster dstOut) {
        return Math.min(src.getHeight(), Math.min(dstIn.getHeight(), dstOut.getHeight()));
    }

    private int computeTransferValue(Object dataElements) {
        return switch (colorModel.getTransferType()) {
            case DataBuffer.TYPE_BYTE -> ((byte[]) dataElements)[0] & 0xff;
            case DataBuffer.TYPE_USHORT, DataBuffer.TYPE_SHORT -> ((short[]) dataElements)[0] & 0xffff;
            case DataBuffer.TYPE_INT -> ((int[]) dataElements)[0];
            default -> 0;
        };
    }

    private Object createTransferArray(int length) {
        return switch (colorModel.getTransferType()) {
            case DataBuffer.TYPE_BYTE -> new byte[length];
            case DataBuffer.TYPE_USHORT, DataBuffer.TYPE_SHORT -> new short[length];
            case DataBuffer.TYPE_INT -> new int[length];
            case DataBuffer.TYPE_FLOAT -> new float[length];
            case DataBuffer.TYPE_DOUBLE -> new double[length];
            default -> null;
        };
    }

    private float brightness(int rgb) {
        int red = rgb >> 16 & 0xff;
        int green = rgb >> 8 & 0xff;
        int blue = rgb & 0xff;
        return RED_BRIGHTNESS_WEIGHT * red
                + GREEN_BRIGHTNESS_WEIGHT * green
                + BLUE_BRIGHTNESS_WEIGHT * blue;
    }

    private boolean needsBrightReplacement(int destinationRgb) {
        return brightness(destinationRgb) < contrastColor.getBrightnessThreshold();
    }

    private Object chooseReplacementDataElements(int destinationRgb) {
        return needsBrightReplacement(destinationRgb) ? brightDataElements : darkDataElements;
    }

    private int chooseReplacementTransferValue(int destinationRgb) {
        return needsBrightReplacement(destinationRgb) ? brightTransferValue : darkTransferValue;
    }

    /// Composes through the generic `getDataElements` and `setDataElements` APIs.
    ///
    /// This path works for any destination model and is also the fallback for uncommon transfer
    /// types that do not expose simpler packed or component-array representations.
    private void composeGenericPixels(Raster src, Raster dstIn, WritableRaster dstOut) {
        int inputX = dstIn.getMinX();
        int inputY = dstIn.getMinY();
        int outputX = dstOut.getMinX();
        int outputY = dstOut.getMinY();
        int width = composeWidth(src, dstIn, dstOut);
        int height = composeHeight(src, dstIn, dstOut);

        Object pixel = null;
        int lastDestinationRgb = WHITE_RGB;
        Object cachedReplacement = darkDataElements;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixel = dstIn.getDataElements(inputX + x, inputY + y, pixel);
                int destinationRgb = colorModel.getRGB(pixel);
                Object replacement = (destinationRgb == lastDestinationRgb)
                        ? cachedReplacement
                        : chooseReplacementDataElements(destinationRgb);
                dstOut.setDataElements(outputX + x, outputY + y, replacement);
                lastDestinationRgb = destinationRgb;
                cachedReplacement = replacement;
            }
        }
    }

    /// Composes through packed transfer values for direct and indexed destination models.
    private void composePackedPixels(Raster src, Raster dstIn, WritableRaster dstOut) {
        switch (colorModel.getTransferType()) {
            case DataBuffer.TYPE_BYTE -> composeBytePackedPixels(src, dstIn, dstOut);
            case DataBuffer.TYPE_USHORT, DataBuffer.TYPE_SHORT -> composeShortPackedPixels(src, dstIn, dstOut);
            case DataBuffer.TYPE_INT -> composeIntPackedPixels(src, dstIn, dstOut);
            default -> composeGenericPixels(src, dstIn, dstOut);
        }
    }

    private void composeBytePackedPixels(Raster src, Raster dstIn, WritableRaster dstOut) {
        int inputX = dstIn.getMinX();
        int inputY = dstIn.getMinY();
        int outputX = dstOut.getMinX();
        int outputY = dstOut.getMinY();
        int width = composeWidth(src, dstIn, dstOut);
        int height = composeHeight(src, dstIn, dstOut);

        byte[] row = new byte[width];
        int lastDestinationRgb = WHITE_RGB;
        int cachedReplacement = darkTransferValue;
        for (int y = 0; y < height; y++) {
            row = (byte[]) dstIn.getDataElements(inputX, inputY + y, width, 1, row);
            for (int x = 0; x < width; x++) {
                int destinationRgb = colorModel.getRGB(row[x] & 0xff);
                int replacement = (destinationRgb == lastDestinationRgb)
                        ? cachedReplacement
                        : chooseReplacementTransferValue(destinationRgb);
                row[x] = (byte) replacement;
                lastDestinationRgb = destinationRgb;
                cachedReplacement = replacement;
            }
            dstOut.setDataElements(outputX, outputY + y, width, 1, row);
        }
    }

    private void composeShortPackedPixels(Raster src, Raster dstIn, WritableRaster dstOut) {
        int inputX = dstIn.getMinX();
        int inputY = dstIn.getMinY();
        int outputX = dstOut.getMinX();
        int outputY = dstOut.getMinY();
        int width = composeWidth(src, dstIn, dstOut);
        int height = composeHeight(src, dstIn, dstOut);

        short[] row = new short[width];
        int lastDestinationRgb = WHITE_RGB;
        int cachedReplacement = darkTransferValue;
        for (int y = 0; y < height; y++) {
            row = (short[]) dstIn.getDataElements(inputX, inputY + y, width, 1, row);
            for (int x = 0; x < width; x++) {
                int destinationRgb = colorModel.getRGB(row[x] & 0xffff);
                int replacement = (destinationRgb == lastDestinationRgb)
                        ? cachedReplacement
                        : chooseReplacementTransferValue(destinationRgb);
                row[x] = (short) replacement;
                lastDestinationRgb = destinationRgb;
                cachedReplacement = replacement;
            }
            dstOut.setDataElements(outputX, outputY + y, width, 1, row);
        }
    }

    private void composeIntPackedPixels(Raster src, Raster dstIn, WritableRaster dstOut) {
        int inputX = dstIn.getMinX();
        int inputY = dstIn.getMinY();
        int outputX = dstOut.getMinX();
        int outputY = dstOut.getMinY();
        int width = composeWidth(src, dstIn, dstOut);
        int height = composeHeight(src, dstIn, dstOut);

        int[] row = new int[width];
        int lastDestinationRgb = WHITE_RGB;
        int cachedReplacement = darkTransferValue;
        for (int y = 0; y < height; y++) {
            row = (int[]) dstIn.getDataElements(inputX, inputY + y, width, 1, row);
            for (int x = 0; x < width; x++) {
                int destinationRgb = colorModel.getRGB(row[x]);
                int replacement = (destinationRgb == lastDestinationRgb)
                        ? cachedReplacement
                        : chooseReplacementTransferValue(destinationRgb);
                row[x] = replacement;
                lastDestinationRgb = destinationRgb;
                cachedReplacement = replacement;
            }
            dstOut.setDataElements(outputX, outputY + y, width, 1, row);
        }
    }

    /// Writes contrasting colors into `dstOut` based on the existing destination/background pixels.
    ///
    /// Only the overlapping span of `src`, `dstIn`, and `dstOut` is processed. `dstIn` supplies
    /// the background colors that determine whether the dark or bright fallback is written. `src`
    /// contributes only the processed extent; this implementation does not inspect source samples.
    @Override
    public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
        if (colorModel instanceof DirectColorModel || colorModel instanceof IndexColorModel) {
            composePackedPixels(src, dstIn, dstOut);
        } else if (colorModel instanceof ComponentColorModel) {
            composeComponentPixels(src, dstIn, dstOut);
        } else {
            composeGenericPixels(src, dstIn, dstOut);
        }
    }

    /// Composes through component-array access for component color models.
    private void composeComponentPixels(Raster src, Raster dstIn, WritableRaster dstOut) {
        int inputX = dstIn.getMinX();
        int inputY = dstIn.getMinY();
        int outputX = dstOut.getMinX();
        int outputY = dstOut.getMinY();
        int width = composeWidth(src, dstIn, dstOut);
        int height = composeHeight(src, dstIn, dstOut);
        int componentCount = colorModel.getNumComponents();
        Object pixel = createTransferArray(componentCount);
        Object row = createTransferArray(width * componentCount);
        if (pixel == null || row == null) {
            composeGenericPixels(src, dstIn, dstOut);
            return;
        }

        int lastDestinationRgb = WHITE_RGB;
        Object cachedReplacement = darkDataElements;
        for (int y = 0; y < height; y++) {
            row = dstIn.getDataElements(inputX, inputY + y, width, 1, row);
            int rowIndex = 0;
            for (int x = 0; x < width; x++) {
                System.arraycopy(row, rowIndex, pixel, 0, componentCount);
                int destinationRgb = colorModel.getRGB(pixel);
                Object replacement = (destinationRgb == lastDestinationRgb)
                        ? cachedReplacement
                        : chooseReplacementDataElements(destinationRgb);
                System.arraycopy(replacement, 0, row, rowIndex, componentCount);
                lastDestinationRgb = destinationRgb;
                cachedReplacement = replacement;
                rowIndex += componentCount;
            }
            dstOut.setDataElements(outputX, outputY + y, width, 1, row);
        }
    }

    /// Releases no resources.
    @Override
    public void dispose() {
    }
}
