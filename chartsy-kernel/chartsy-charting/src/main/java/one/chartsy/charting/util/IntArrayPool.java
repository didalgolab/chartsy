package one.chartsy.charting.util;

import java.io.PrintStream;

/// Exposes the shared [ArrayPool] instance used for `int[]` scratch buffers.
///
/// `PlotStyle` and `DataPoints` borrow arrays from this facade during temporary coordinate and
/// index calculations. Pooling policy, borrower tracking, and size thresholds come from the
/// underlying [ArrayPool].
public class IntArrayPool {
    private static final ArrayPool pool = new ArrayPool("IntArrayPool") {
        
        @Override
        protected Object create(int size) {
            return new int[size];
        }
        
        @Override
        protected int getSize(Object array) {
            return ((int[]) array).length;
        }
    };
    // ("IntArrayPool");
    
    /// Prints the current pool diagnostic summary to a stream.
    public static void printInfo(PrintStream stream) {
        stream.println(IntArrayPool.pool);
    }
    
    /// Returns an array with at least `size` slots, copying the existing contents when reallocation is required.
    public static int[] reAlloc(int[] array, int size, Object borrower) {
        return (int[]) IntArrayPool.pool.reAlloc(array, size, borrower);
    }
    
    /// Returns a previously borrowed array to the shared pool when pooling is enabled for its size.
    public static void release(int[] array) {
        IntArrayPool.pool.release(array);
    }
    
    /// Borrows an array without registering a borrower token for automatic reclamation.
    public static int[] take(int size) {
        return IntArrayPool.take(size, null);
    }
    
    /// Borrows an array with at least `size` slots and optionally associates it with a borrower token.
    public static int[] take(int size, Object borrower) {
        return (int[]) IntArrayPool.pool.take(size, borrower);
    }
    
    private IntArrayPool() {
    }
}
