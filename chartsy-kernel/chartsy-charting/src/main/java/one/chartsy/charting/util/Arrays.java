package one.chartsy.charting.util;

import java.util.Random;

/// Provides internal helpers for `double[]` growth, seeded pseudo-random sample generation, and
/// in-place prefix reversal.
///
/// [DoubleArray] uses the package-private capacity helpers so backing arrays grow in powers of
/// two with a minimum size of `4`.
public class Arrays {
    
    /// Generates bounded pseudo-random `double` sequences used by [#randomValues(int, double, double)].
    ///
    /// All instances share one JVM-wide random source seeded with `41538L`.
    static class DoubleGenerator {
        private static final Random RANDOM = new Random(41_538L);
        private final double minValue;
        private final double maxValue;
        private final double valueRange;
        private final double volatilityScale;
        private final int spikeFrequency;
        
        /// Creates a generator bounded to `0..1000` with low volatility and effectively disabled
        /// spikes.
        public DoubleGenerator() {
            this(0.0, 1000.0, 0.01, Integer.MAX_VALUE);
        }
        
        /// Creates a bounded pseudo-random walk generator.
        ///
        /// Steps are sampled from a range derived from `maxValue - minValue`, then clamped back
        /// to `minValue..maxValue`.
        ///
        /// @param minValue
        ///     lower bound for generated values
        /// @param maxValue
        ///     upper bound for generated values
        /// @param volatilityScale
        ///     step amplitude relative to `maxValue - minValue`
        /// @param spikeFrequency
        ///     modulus used to occasionally double a sampled step; `0` causes
        ///     [ArithmeticException] when sampling
        public DoubleGenerator(double minValue, double maxValue, double volatilityScale,
                               int spikeFrequency) {
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.spikeFrequency = spikeFrequency;
            this.volatilityScale = volatilityScale;
            valueRange = maxValue - minValue;
        }
        
        /// Generates a bounded sequence.
        ///
        /// The first value is `minValue`. For sequences shorter than `1000`, each subsequent value
        /// is sampled independently from [#nextValue(double)]. Longer sequences repeat each sampled
        /// delta for `count / 500` additional points, creating piecewise-linear stretches.
        ///
        /// @param count
        ///     number of values to generate
        /// @return
        ///     generated values of length `count`
        /// @throws ArrayIndexOutOfBoundsException
        ///     if `count` is `0`
        /// @throws NegativeArraySizeException
        ///     if `count` is negative
        public double[] generate(int count) {
            double[] values = new double[count];
            int continuationLength = (count < 1000) ? 0 : count / 500;
            values[0] = minValue;
            double previousValue = values[0];

            for (int index = 1; index < count;) {
                double sampledValue = nextValue(previousValue);
                values[index++] = sampledValue;

                double delta = sampledValue - previousValue;
                for (int i = 0; i < continuationLength && index < count; i++, index++) {
                    double continuedValue = values[index - 1] + delta;
                    if (continuedValue < minValue)
                        continuedValue = minValue;
                    else if (continuedValue > maxValue)
                        continuedValue = maxValue;
                    values[index] = continuedValue;
                }

                previousValue = values[index - 1];
            }
            return values;
        }
        
        /// Samples the next random-walk value from `previousValue`.
        ///
        /// @param previousValue
        ///     current value in the sequence
        /// @return
        ///     next value clamped to `minValue..maxValue`
        /// @throws ArithmeticException
        ///     if `spikeFrequency` is `0`
        public double nextValue(double previousValue) {
            double delta = valueRange * (1.0 - 2.0 * RANDOM.nextDouble()) * volatilityScale;
            if (RANDOM.nextInt() % spikeFrequency == 0)
                delta *= 2.0;

            double nextValue = previousValue + delta;
            if (nextValue < minValue)
                nextValue = minValue;
            else if (nextValue > maxValue)
                nextValue = maxValue;
            return nextValue;
        }
    }
    
    /// Returns a copy of `source` with a normalized capacity.
    ///
    /// The returned array length is [#normalizeCapacity(int)] of `newSize`.
    ///
    /// @param source
    ///     source array to copy
    /// @param newSize
    ///     requested minimum capacity before normalization
    /// @return
    ///     copied array with normalized capacity
    /// @throws IllegalArgumentException
    ///     if the normalized size is smaller than `source.length`
    static double[] growDoubleArray(double[] source, int newSize) {
        int normalizedSize = normalizeCapacity(newSize);
        if (normalizedSize < source.length)
            throw new IllegalArgumentException("New size must be greater than current size");
        double[] grown = new double[normalizedSize];
        System.arraycopy(source, 0, grown, 0, source.length);
        return grown;
    }
    
    /// Rounds a requested capacity up to a power of two, with a minimum of `4`.
    ///
    /// @param requestedSize
    ///     requested capacity
    /// @return
    ///     normalized capacity used by package-local array growth
    static int normalizeCapacity(int requestedSize) {
        if (requestedSize < 4)
            return 4;

        int capacity = 4;
        while (capacity < requestedSize)
            capacity <<= 1;
        return capacity;
    }
    
    /// Generates bounded sample values from a shared seeded pseudo-random source.
    ///
    /// Results depend on prior use of that shared generator, so output is reproducible only for a
    /// fixed overall call order.
    ///
    /// This is a convenience wrapper around [DoubleGenerator] with `volatilityScale = 0.05` and
    /// `spikeFrequency = 100`.
    ///
    /// @param count
    ///     number of values to generate
    /// @param minValue
    ///     lower bound for generated values
    /// @param maxValue
    ///     upper bound for generated values
    /// @return
    ///     generated values
    public static double[] randomValues(int count, double minValue, double maxValue) {
        return new DoubleGenerator(minValue, maxValue, 0.05, 100).generate(count);
    }
    
    /// Reverses the first `length` elements of `values` in place.
    ///
    /// @param values
    ///     array whose prefix should be reversed
    /// @param length
    ///     number of leading elements to reverse
    /// @throws ArrayIndexOutOfBoundsException
    ///     if `length` is greater than `values.length`
    public static void reverse(double[] values, int length) {
        int left = 0;
        int right = length - 1;
        while (left < right) {
            double value = values[left];
            values[left] = values[right];
            values[right] = value;
            left++;
            right--;
        }
    }
    
    private Arrays() {
    }
}
