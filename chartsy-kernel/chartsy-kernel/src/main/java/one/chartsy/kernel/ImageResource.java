/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel;

import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable-by-convention image resource backed by bytes with an auto-detected MIME type.
 * <p>
 * Note: {@link ByteArrayResource} exposes the underlying byte array via {@link #getByteArray()},
 * so callers must not mutate it.
 */
public final class ImageResource extends ByteArrayResource {

    public static final String MIME_SVG = "image/svg+xml";

    private static final AtomicLong IMAGE_ID = new AtomicLong(1);

    // Gemini-supported image MIME types (plus SVG for local usage).
    public static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            MIME_SVG,
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/heic",
            "image/heif"
    );

    private final String mimeType;

    public ImageResource(byte[] bytes) {
        this(bytes, "#" + IMAGE_ID.getAndIncrement());
    }

    public ImageResource(byte[] bytes, String description) {
        super(requireNonEmpty(bytes), description);
        this.mimeType = detectAndValidateMimeType(bytes);
    }

    public String getMimeType() {
        return mimeType;
    }

    public byte[] bytes() {
        return getByteArray();
    }

    private static byte[] requireNonEmpty(byte[] bytes) {
        if (bytes == null)
            throw new IllegalArgumentException("bytes is null");
        if (bytes.length == 0)
            throw new IllegalArgumentException("bytes is empty");
        return bytes;
    }

    private static String detectAndValidateMimeType(byte[] bytes) {
        String mimeType = detectMimeType(bytes);
        if (!SUPPORTED_MIME_TYPES.contains(mimeType))
            throw new IllegalArgumentException("Unsupported image MIME type: " + mimeType);
        return mimeType;
    }

    private static String detectMimeType(byte[] bytes) {
        if (looksLikeSvgXml(bytes))
            return MIME_SVG;
        if (isPng(bytes))
            return "image/png";
        if (isJpeg(bytes))
            return "image/jpeg";
        if (isWebp(bytes))
            return "image/webp";

        String heif = detectHeifFamily(bytes);
        if (heif != null)
            return heif;

        throw new IllegalArgumentException("Unrecognized image format (no supported magic header found)");
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private static boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private static String detectHeifFamily(byte[] bytes) {
        if (bytes.length < 12)
            return null;
        if (!(bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p'))
            return null;

        String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII);

        if (brand.equals("heic") || brand.equals("heix") || brand.equals("hevc") || brand.equals("hevx"))
            return "image/heic";

        if (brand.equals("mif1") || brand.equals("msf1") || brand.startsWith("hei"))
            return "image/heif";

        return null;
    }

    private static boolean looksLikeSvgXml(byte[] bytes) {
        int i = 0;
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < bytes.length) {
            byte c = bytes[i];
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t')
                i++;
            else
                break;
        }

        int scanLen = Math.min(bytes.length - i, 4096);
        if (scanLen <= 0)
            return false;

        String prefix = new String(bytes, i, scanLen, StandardCharsets.UTF_8).toLowerCase();
        return prefix.contains("<svg");
    }
}
