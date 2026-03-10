package one.chartsy.study;

import java.util.Locale;

public record StudyColor(int rgba) {

    public static StudyColor of(int rgba) {
        return new StudyColor(rgba);
    }

    public static StudyColor fromRgb(int rgb) {
        return new StudyColor(0xFF000000 | (rgb & 0x00FFFFFF));
    }

    public static StudyColor parse(String text) {
        if (text == null || text.isBlank())
            throw new IllegalArgumentException("Color text is blank");

        String hex = text.trim();
        if (hex.startsWith("#"))
            hex = hex.substring(1);
        if (hex.startsWith("0x") || hex.startsWith("0X"))
            hex = hex.substring(2);

        int value = switch (hex.length()) {
            case 6 -> 0xFF000000 | Integer.parseUnsignedInt(hex, 16);
            case 8 -> (int) Long.parseUnsignedLong(hex, 16);
            default -> throw new IllegalArgumentException("Unsupported color literal: " + text);
        };
        return new StudyColor(value);
    }

    public int alpha() {
        return rgba >>> 24;
    }

    public int red() {
        return (rgba >>> 16) & 0xFF;
    }

    public int green() {
        return (rgba >>> 8) & 0xFF;
    }

    public int blue() {
        return rgba & 0xFF;
    }

    public int rgb() {
        return rgba & 0x00FFFFFF;
    }

    public String toHex() {
        return alpha() == 0xFF
                ? String.format(Locale.ROOT, "#%06X", rgb())
                : String.format(Locale.ROOT, "#%08X", rgba);
    }
}
