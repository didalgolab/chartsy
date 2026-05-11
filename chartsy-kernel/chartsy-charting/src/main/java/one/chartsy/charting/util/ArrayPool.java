package one.chartsy.charting.util;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/// Pools large array instances and tracks borrowers so reclaimed owners can return arrays automatically.
///
/// Arrays smaller than {@link #SIZE_THRESHOLD} are never pooled and are allocated directly.
/// For pooled arrays, callers may supply a `borrower` token to {@link #take(int, Object)} or
/// {@link #reAlloc(Object, int, Object)}. When that borrower becomes unreachable, the pool
/// observes its phantom reference and moves the corresponding array back to the available list.
///
/// All instance methods that mutate pool state are synchronized.
public abstract class ArrayPool {

    /// Represents one pooled array entry managed by {@link ArrayPool}.
    ///
    /// The soft reference allows the backing array to be reclaimed under memory pressure while
    /// preserving the cached size bucket. During lending, the block may also hold a phantom
    /// reference to a borrower token so {@link #checkQueue()} can move the block back to the
    /// available list when that borrower becomes unreachable.
    ///
    /// Instances are mutated only under the outer pool lock.
    private final class Block extends SoftReference<Object> {
        private final int arraySize;
        private PhantomReference<Object> borrowerReference;

        /// Creates a tracked block for an array and registers it in the pooled-array queue.
        ///
        /// @param array backing array tracked by this block
        /// @param arraySize logical capacity bucket for this block
        private Block(Object array, int arraySize) {
            super(array, pooledArrayReferenceQueue);
            this.arraySize = arraySize;
        }

        /// Associates this block with a borrower token.
        ///
        /// The token is optional. When present, its phantom-reference enqueue event signals that
        /// the array can be automatically returned to the available pool.
        ///
        /// @param borrower logical owner of the borrowed array, or `null` to disable owner tracking
        private void markBorrowed(Object borrower) {
            if (borrower != null)
                borrowerReference = new PhantomReference<>(borrower, borrowerReferenceQueue);
        }

        /// Clears borrower tracking after manual or automatic return to available blocks.
        private void clearBorrower() {
            borrowerReference = null;
        }

        @Override
        public String toString() {
            String text = "Block [" + arraySize + "]";
            if (get() == null)
                text += " (null)";
            return text;
        }
    }

    private static final Object INT_COORDS_LOCK = new Object();
    private static final int[][] SHARED_INT_COORDS = new int[2][];

    /// Minimum length for arrays that are tracked by this pool.
    ///
    /// Requests smaller than this value bypass pooling and always allocate a new array.
    public static int SIZE_THRESHOLD = 129;

    /// Rounds a requested capacity up to the next power-of-two bucket used by pooled growth.
    ///
    /// @param requestedSize minimum required length
    /// @return a power of two that is greater than or equal to `requestedSize`, with floor `4`
    static int roundUpToPowerOfTwo(int requestedSize) {
        if (requestedSize < 4)
            return 4;
        int capacity = 4;
        while (true) {
            if (capacity >= requestedSize)
                break;
            capacity <<= 1;
        }
        return capacity;
    }

    /// Returns a shared `(x, y)` integer coordinate buffer pair.
    ///
    /// Callers must synchronize on {@link #getIntCoordsLock()} while using the returned arrays.
    /// The returned arrays are reused across calls and may be replaced with larger arrays.
    ///
    /// @param size minimum required coordinate count
    /// @return two reusable integer arrays where index `0` is x-coordinates and index `1` is y-coordinates
    public static int[][] allocIntCoords(int size) {
        if (SHARED_INT_COORDS[0] == null || SHARED_INT_COORDS[0].length < size) {
            SHARED_INT_COORDS[0] = new int[size];
            SHARED_INT_COORDS[1] = new int[size];
        }
        return SHARED_INT_COORDS;
    }

    /// Returns the monitor object guarding {@link #allocIntCoords(int)} shared buffers.
    public static final Object getIntCoordsLock() {
        return INT_COORDS_LOCK;
    }

    private final ReferenceQueue<Object> pooledArrayReferenceQueue;
    private final ReferenceQueue<Object> borrowerReferenceQueue;
    private final List<Block> availableBlocks;
    private final List<Block> borrowedBlocks;
    private final String poolName;

    ArrayPool(String poolName) {
        this(poolName, false);
    }

    ArrayPool(String poolName, boolean printDebugInfo) {
        pooledArrayReferenceQueue = new ReferenceQueue<>();
        borrowerReferenceQueue = new ReferenceQueue<>();
        availableBlocks = new LinkedList<>();
        borrowedBlocks = new LinkedList<>();
        this.poolName = poolName;
        if (printDebugInfo) {
            Thread debugThread = new Thread(() -> {
                while (true)
                    try {
                        Thread.sleep(2000L);
                        System.out.println(ArrayPool.this);
                    } catch (InterruptedException e) {
                        return;
                    }
            });
            debugThread.start();
        }
    }

    /// Processes pending reference-queue events and reconciles internal block lists.
    ///
    /// Soft-reference events remove cleared arrays from either available or borrowed lists.
    /// Borrower phantom-reference events move blocks from borrowed back to available.
    public void checkQueue() {
        Reference<?> clearedArrayReference = pooledArrayReferenceQueue.poll();
        while (clearedArrayReference != null) {
            if (!availableBlocks.remove(clearedArrayReference))
                borrowedBlocks.remove(clearedArrayReference);
            clearedArrayReference = pooledArrayReferenceQueue.poll();
        }
        Reference<?> clearedBorrowerReference = borrowerReferenceQueue.poll();
        while (clearedBorrowerReference != null) {
            Iterator<Block> borrowedIterator = borrowedBlocks.iterator();
            while (borrowedIterator.hasNext()) {
                Block borrowedBlock = borrowedIterator.next();
                if (borrowedBlock.borrowerReference == clearedBorrowerReference) {
                    borrowedIterator.remove();
                    borrowedBlock.clearBorrower();
                    availableBlocks.add(borrowedBlock);
                    clearedBorrowerReference.clear();
                }
            }
            clearedBorrowerReference = borrowerReferenceQueue.poll();
        }
    }

    /// Creates a new backing array of the requested size.
    ///
    /// @param size requested array length
    /// @return a new array instance compatible with this pool
    protected abstract Object create(int size);

    /// Locates a currently borrowed block that references the given array instance.
    ///
    /// @param array array instance to find in borrowed blocks
    /// @return borrowed block for `array`, or `null` when the array is not tracked as borrowed
    protected Block findBorrowedBlockByArray(Object array) {
        Iterator<Block> borrowedIterator = borrowedBlocks.iterator();
        while (borrowedIterator.hasNext()) {
            Block borrowedBlock = borrowedIterator.next();
            if (borrowedBlock.get() == array)
                return borrowedBlock;
        }
        return null;
    }

    /// Returns the length of a pooled array instance.
    ///
    /// @param array pooled array instance
    /// @return array length
    protected abstract int getSize(Object array);

    /// Ensures the returned array can hold at least `minSize` elements.
    ///
    /// If `array` is already large enough, it is returned unchanged. Otherwise, this method
    /// borrows a larger array, copies `getSize(array)` elements from the start, and releases the old array.
    ///
    /// @param array existing array, or `null` to allocate a new one
    /// @param minSize required minimum length
    /// @param borrower borrower token used for automatic return when it is garbage-collected
    /// @return original array when capacity is sufficient, otherwise a larger replacement array
    public synchronized Object reAlloc(Object array, int minSize, Object borrower) {
        if (array == null)
            return take(minSize, borrower);
        int currentSize = getSize(array);
        if (currentSize >= minSize)
            return array;
        int targetSize = roundUpToPowerOfTwo(minSize);
        Object replacement = take(targetSize, borrower);
        System.arraycopy(array, 0, replacement, 0, currentSize);
        release(array);
        return replacement;
    }

    /// Returns a borrowed array to the pool.
    ///
    /// Arrays smaller than {@link #SIZE_THRESHOLD} are ignored because they are never pooled.
    /// Releasing an untracked array is a no-op.
    ///
    /// @param array borrowed array to return
    public synchronized void release(Object array) {
        if (array == null || getSize(array) < SIZE_THRESHOLD)
            return;

        Block borrowedBlock = findBorrowedBlockByArray(array);
        if (borrowedBlock != null) {
            borrowedBlocks.remove(borrowedBlock);
            availableBlocks.add(borrowedBlock);
            borrowedBlock.clearBorrower();
        }
    }

    /// Borrows an array with length at least `minSize`.
    ///
    /// Arrays smaller than {@link #SIZE_THRESHOLD} bypass pooling and are created directly.
    /// For pooled requests, the pool prefers an available block with sufficient capacity and
    /// otherwise allocates a fresh array while optionally discarding one undersized cached block.
    ///
    /// @param minSize minimum required array length
    /// @param borrower borrower token used for automatic return when it is garbage-collected
    /// @return borrowed array whose length is at least `minSize`
    public synchronized Object take(int minSize, Object borrower) {
        if (minSize < SIZE_THRESHOLD)
            return create(minSize);
        checkQueue();
        ListIterator<Block> availableIterator = availableBlocks.listIterator();
        Block selectedBlock = null;
        Block largestTooSmallBlock = null;
        Object selectedArray = null;
        block: {
            Block candidateBlock;
            while (true) {
                if (!availableIterator.hasNext())
                    break block;
                candidateBlock = availableIterator.next();
                if (candidateBlock.arraySize >= minSize)
                    break;
                if (largestTooSmallBlock != null)
                    if (largestTooSmallBlock.arraySize >= candidateBlock.arraySize)
                        continue;
                largestTooSmallBlock = candidateBlock;
            }
            selectedBlock = candidateBlock;
            selectedArray = candidateBlock.get();
        } // end block
        
        if (selectedArray != null)
            availableIterator.remove();
        else if (selectedBlock != null) {
            availableIterator.remove();
            selectedArray = create(minSize);
            selectedBlock = new Block(selectedArray, minSize);
        } else {
            if (largestTooSmallBlock != null)
                availableBlocks.remove(largestTooSmallBlock);
            selectedArray = create(minSize);
            selectedBlock = new Block(selectedArray, minSize);
        }
        selectedBlock.markBorrowed(borrower);
        borrowedBlocks.add(selectedBlock);
        return selectedArray;
    }

    @Override
    public synchronized String toString() {
        return poolName + " ---------------------------------------\n" + "Availables[" +
                availableBlocks.size() + "]:\t" + availableBlocks + "\nBorrowed[" + borrowedBlocks.size() +
                "]:\t" + borrowedBlocks;
    }
}
