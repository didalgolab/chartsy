package one.chartsy.data;

/**
 * Primitive equivalent of {@code Series} suitable for holding double values.
 */
public interface DoubleSeries extends TimeSeriesAlike {

    double get(int index);

    DoubleDataset values();
}
