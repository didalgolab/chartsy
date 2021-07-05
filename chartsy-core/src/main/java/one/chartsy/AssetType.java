package one.chartsy;

/**
 * The type of a financial instrument.
 */
public interface AssetType {
    /**
     * The financial asset type identifier.
     */
    String name();

    /**
     * Yields {@code true} if the referred instrument type is tradable, and yields {@code false} otherwise.
     */
    boolean isTradable();
}
