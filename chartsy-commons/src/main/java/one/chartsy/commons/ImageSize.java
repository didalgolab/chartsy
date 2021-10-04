package one.chartsy.commons;

public enum ImageSize {
    TINY(8),
    SMALL(16),
    MEDIUM(24),
    LARGE(32);

    public int getIconSize() {
        return iconSize;
    }

    ImageSize(int iconSize) {
        this.iconSize = iconSize;
    }
    private final int iconSize;
}
