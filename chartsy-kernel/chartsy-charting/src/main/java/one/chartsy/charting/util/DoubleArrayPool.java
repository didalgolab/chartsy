package one.chartsy.charting.util;

/// Exposes the shared [ArrayPool] instance used for `double[]` scratch buffers.
///
/// `DoublePoints`, `DoubleArray`, and other renderer helpers borrow arrays from this facade instead
/// of allocating large temporary buffers repeatedly. Pooling policy, borrower tracking, and
/// threshold behavior come from the underlying [ArrayPool].
public final class DoubleArrayPool {
    private static final ArrayPool pool = new ArrayPool("DoubleArrayPool") {
        
        @Override
        protected Object create(int size) {
            return new double[size];
        }
        
        @Override
        protected int getSize(Object array) {
            return ((double[]) array).length;
        }
    };
    
    /// Returns an array with at least `size` slots, copying the existing contents when reallocation is required.
    public static double[] reAlloc(double[] array, int size, Object borrower) {
        return (double[]) DoubleArrayPool.pool.reAlloc(array, size, borrower);
    }
    
    /// Returns a previously borrowed array to the shared pool when pooling is enabled for its size.
    public static void release(double[] array) {
        DoubleArrayPool.pool.release(array);
    }
    
    /// Borrows an array without registering a borrower token for automatic reclamation.
    public static double[] take(int size) {
        return DoubleArrayPool.take(size, null);
    }
    
    /// Borrows an array with at least `size` slots and optionally associates it with a borrower token.
    public static double[] take(int size, Object borrower) {
        return (double[]) DoubleArrayPool.pool.take(size, borrower);
    }
    
    private DoubleArrayPool() {
    }
}
