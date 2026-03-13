package one.chartsy.charting.util;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import one.chartsy.charting.util.collections.BalancedBinaryTree;

/// Stores disjoint inclusive integer intervals and coalesces overlaps or direct
/// adjacencies.
///
/// The set is optimized for charting update paths that repeatedly add, subtract, and
/// iterate changed index ranges. Internally each stored interval keeps its upper bound in
/// exclusive form even though the public API and {@link IntInterval} views expose
/// inclusive endpoints.
///
/// ### API Notes
///
/// - [#add(int, int)] merges both overlapping and directly adjacent spans so repeated
///   event batches collapse into the smallest number of intervals the chart needs to
///   replay.
/// - [#remove(int, int)] uses the same inclusive coordinate system and may shrink,
///   delete, or split previously stored spans.
/// - [#size()] reports how many disjoint spans are stored, not how many individual
///   integer indexes are covered.
public class IntIntervalSet {

    /// Internal tree entry representing one stored interval.
    ///
    /// The lower bound is inclusive and the upper bound is kept in exclusive form so
    /// adjacency checks can treat `value == exclusiveEnd` as the first uncovered value
    /// after the interval.
    static class IntervalEntry extends BalancedBinaryTree.Entry implements IntInterval {
        int first;
        int exclusiveEnd;

        IntervalEntry(int first, int exclusiveEnd) {
            this.first = first;
            this.exclusiveEnd = exclusiveEnd;
        }

        @Override
        public int getFirst() {
            return first;
        }

        @Override
        public int getLast() {
            return exclusiveEnd - 1;
        }

        @Override
        public String toString() {
            return "+[" + getFirst() + "," + getLast() + "]";
        }
    }

    private final BalancedBinaryTree tree;

    private transient int modCount;

    public IntIntervalSet() {
        tree = new BalancedBinaryTree();
    }

    private static int getNodeLocalIndex(IntervalEntry entry) {
        BalancedBinaryTree.Node node = entry.getHoldingNode();
        int entryCount = node.getEntriesCount();
        if (entryCount <= 1)
            return 0;

        int exclusiveEnd = entry.exclusiveEnd;
        int low = 0;
        int high = entryCount - 1;
        while (low <= high) {
            int mid = low + ((high - low) >> 1);
            if (entryAt(node, mid).exclusiveEnd >= exclusiveEnd)
                high = mid - 1;
            else
                low = mid + 1;
        }

        assert node.getEntry(low) == entry;
        return low;
    }

    private static IntervalEntry entryAt(BalancedBinaryTree.Node node, int index) {
        return (IntervalEntry) node.getEntry(index);
    }

    private static IntervalEntry lastEntryOf(BalancedBinaryTree.Node node) {
        return entryAt(node, node.getEntriesCount() - 1);
    }

    private static int findFirstIndexWithExclusiveEndAtLeast(BalancedBinaryTree.Node node, int value) {
        int low = 0;
        int high = node.getEntriesCount() - 1;
        while (low < high) {
            int mid = low + ((high - low) >> 1);
            if (entryAt(node, mid).exclusiveEnd >= value)
                high = mid;
            else
                low = mid + 1;
        }
        return low;
    }

    private static int findFirstIndexWithExclusiveEndGreaterThan(BalancedBinaryTree.Node node, int value) {
        int low = 0;
        int high = node.getEntriesCount() - 1;
        while (low < high) {
            int mid = low + ((high - low) >> 1);
            if (entryAt(node, mid).exclusiveEnd > value)
                high = mid;
            else
                low = mid + 1;
        }
        return low;
    }

    private static int findLastIndexWithFirstAtMost(BalancedBinaryTree.Node node, int value) {
        int low = 0;
        int high = node.getEntriesCount() - 1;
        while (low < high) {
            int mid = low + ((high - low + 1) >> 1);
            if (entryAt(node, mid).first <= value)
                low = mid;
            else
                high = mid - 1;
        }
        return low;
    }

    private static int findLastIndexWithFirstLessThan(BalancedBinaryTree.Node node, int value) {
        int low = 0;
        int high = node.getEntriesCount() - 1;
        while (low < high) {
            int mid = low + ((high - low + 1) >> 1);
            if (entryAt(node, mid).first < value)
                low = mid;
            else
                high = mid - 1;
        }
        return low;
    }

    private IntervalEntry firstEntry() {
        return (tree.getSize() == 0) ? null : (IntervalEntry) tree.getEntryAt(0);
    }

    private IntervalEntry findFirstEntryEndingAtOrAfter(int value) {
        BalancedBinaryTree.Node node = tree.getRoot();
        IntervalEntry candidate = null;
        while (node != null) {
            if (lastEntryOf(node).exclusiveEnd < value) {
                node = node.getRightBranch();
                continue;
            }

            int index = findFirstIndexWithExclusiveEndAtLeast(node, value);
            candidate = entryAt(node, index);
            node = (index == 0) ? node.getLeftBranch() : null;
        }
        return candidate;
    }

    private IntervalEntry findFirstEntryEndingAfter(int value) {
        BalancedBinaryTree.Node node = tree.getRoot();
        IntervalEntry candidate = null;
        while (node != null) {
            if (lastEntryOf(node).exclusiveEnd <= value) {
                node = node.getRightBranch();
                continue;
            }

            int index = findFirstIndexWithExclusiveEndGreaterThan(node, value);
            candidate = entryAt(node, index);
            node = (index == 0) ? node.getLeftBranch() : null;
        }
        return candidate;
    }

    private IntervalEntry findLastEntryStartingAtOrBefore(int value) {
        BalancedBinaryTree.Node node = tree.getRoot();
        IntervalEntry candidate = null;
        while (node != null) {
            if (entryAt(node, 0).first > value) {
                node = node.getLeftBranch();
                continue;
            }

            int index = findLastIndexWithFirstAtMost(node, value);
            candidate = entryAt(node, index);
            node = (index == node.getEntriesCount() - 1) ? node.getRightBranch() : null;
        }
        return candidate;
    }

    private IntervalEntry findLastEntryStartingBefore(int value) {
        BalancedBinaryTree.Node node = tree.getRoot();
        IntervalEntry candidate = null;
        while (node != null) {
            if (entryAt(node, 0).first >= value) {
                node = node.getLeftBranch();
                continue;
            }

            int index = findLastIndexWithFirstLessThan(node, value);
            candidate = entryAt(node, index);
            node = (index == node.getEntriesCount() - 1) ? node.getRightBranch() : null;
        }
        return candidate;
    }

    private void appendEntry(IntervalEntry entry) {
        BalancedBinaryTree.Node node = tree.getRoot();
        if (node == null) {
            tree.insertEntryAtRoot(entry);
            return;
        }

        while (node.getRightBranch() != null)
            node = node.getRightBranch();

        tree.insertEntryAfter(node, entry);
    }

    private void prependEntry(IntervalEntry entry) {
        BalancedBinaryTree.Node node = tree.getRoot();
        if (node == null) {
            tree.insertEntryAtRoot(entry);
            return;
        }

        while (node.getLeftBranch() != null)
            node = node.getLeftBranch();

        tree.insertEntryBefore(node, entry);
    }

    private void insertEntryBetween(IntervalEntry previousEntry, IntervalEntry nextEntry, IntervalEntry entry) {
        BalancedBinaryTree.Node previousNode = previousEntry.getHoldingNode();
        BalancedBinaryTree.Node nextNode = nextEntry.getHoldingNode();
        if (previousNode == nextNode) {
            tree.insertEntryInto(nextNode, getNodeLocalIndex(nextEntry), entry);
            return;
        }
        if (previousNode.getRightBranch() == null) {
            tree.insertEntryAfter(previousNode, entry);
            return;
        }
        tree.insertEntryBefore(nextNode, entry);
    }

    private void insertEntryAfter(IntervalEntry anchor, IntervalEntry entry) {
        BalancedBinaryTree.Node node = anchor.getHoldingNode();
        int insertIndex = getNodeLocalIndex(anchor) + 1;
        if (insertIndex == node.getEntriesCount())
            tree.insertEntryAfter(node, entry);
        else
            tree.insertEntryInto(node, insertIndex, entry);
    }

    private void deleteEntries(IntervalEntry firstEntry, IntervalEntry afterLastEntry) {
        IntervalEntry current = firstEntry;
        while (current != null && current != afterLastEntry) {
            IntervalEntry next = nextEntry(current);
            tree.deleteEntry(current);
            current = next;
        }
    }

    /// Adds an inclusive interval to the set.
    ///
    /// Overlapping or directly adjacent stored intervals are merged into one interval.
    /// Calls with `first > last` are ignored.
    ///
    /// @param first inclusive lower bound
    /// @param last inclusive upper bound
    public void add(int first, int last) {
        if (first > last)
            return;

        int exclusiveEnd = last + 1;
        IntervalEntry firstIntersectingOrAdjacent = findFirstEntryEndingAtOrAfter(first);
        if (firstIntersectingOrAdjacent == null) {
            modCount++;
            appendEntry(new IntervalEntry(first, exclusiveEnd));
            return;
        }

        IntervalEntry lastIntersectingOrAdjacent = findLastEntryStartingAtOrBefore(exclusiveEnd);
        if (lastIntersectingOrAdjacent == null) {
            modCount++;
            prependEntry(new IntervalEntry(first, exclusiveEnd));
            return;
        }

        if (firstIntersectingOrAdjacent.first > first
                && lastIntersectingOrAdjacent.exclusiveEnd < firstIntersectingOrAdjacent.first) {
            modCount++;
            insertEntryBetween(lastIntersectingOrAdjacent, firstIntersectingOrAdjacent,
                    new IntervalEntry(first, exclusiveEnd));
            return;
        }

        int mergedFirst = Math.min(first, firstIntersectingOrAdjacent.first);
        int mergedExclusiveEnd = Math.max(exclusiveEnd, firstIntersectingOrAdjacent.exclusiveEnd);
        boolean changed = firstIntersectingOrAdjacent != lastIntersectingOrAdjacent
                || mergedFirst != firstIntersectingOrAdjacent.first
                || mergedExclusiveEnd != firstIntersectingOrAdjacent.exclusiveEnd;
        if (!changed)
            return;

        modCount++;
        if (firstIntersectingOrAdjacent != lastIntersectingOrAdjacent) {
            IntervalEntry afterMergedEntries = nextEntry(lastIntersectingOrAdjacent);
            IntervalEntry current = nextEntry(firstIntersectingOrAdjacent);
            while (current != null && current != afterMergedEntries) {
                mergedExclusiveEnd = Math.max(mergedExclusiveEnd, current.exclusiveEnd);
                IntervalEntry next = nextEntry(current);
                tree.deleteEntry(current);
                current = next;
            }
        }

        firstIntersectingOrAdjacent.first = mergedFirst;
        firstIntersectingOrAdjacent.exclusiveEnd = mergedExclusiveEnd;
    }

    private IntervalEntry nextEntry(IntervalEntry entry) {
        BalancedBinaryTree.Node node = entry.getHoldingNode();
        int localIndex = getNodeLocalIndex(entry);
        if (localIndex + 1 < node.getEntriesCount())
            return entryAt(node, localIndex + 1);

        node = tree.getSuccessor(node);
        return (node == null) ? null : entryAt(node, 0);
    }

    /// Removes every stored interval.
    ///
    /// Outstanding iterators become invalid even when the set was already empty.
    public void clear() {
        tree.deleteAll();
        modCount++;
    }

    /// Checks whether a value is covered by any stored interval.
    ///
    /// @param value value to test
    /// @return `true` when `value` falls inside at least one stored interval
    public boolean contains(int value) {
        IntervalEntry candidate = findFirstEntryEndingAfter(value);
        return candidate != null && candidate.first <= value;
    }

    /// Returns a fail-fast iterator over the stored intervals in ascending order.
    ///
    /// The iterator is backed by this set rather than a snapshot. Its `remove()` method
    /// deletes the last returned interval from the backing set, and any other structural
    /// change triggers the usual fail-fast check on the next mutating or advancing call.
    public Iterator<IntInterval> intervalIterator() {
        return new Iterator<>() {
            private int expectedModCount = modCount;
            private IntervalEntry lastReturned;
            private IntervalEntry next = firstEntry();

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public IntInterval next() {
                if (next == null)
                    throw new NoSuchElementException();
                if (modCount != expectedModCount)
                    throw new ConcurrentModificationException();

                lastReturned = next;
                next = nextEntry(next);
                return lastReturned;
            }

            @Override
            public void remove() {
                if (lastReturned == null)
                    throw new IllegalStateException();
                if (modCount != expectedModCount)
                    throw new ConcurrentModificationException();

                tree.deleteEntry(lastReturned);
                modCount++;
                expectedModCount++;
                lastReturned = null;
            }
        };
    }

    /// Removes an inclusive interval from the set.
    ///
    /// Existing intervals may shrink, disappear, or split into two intervals. Calls with
    /// `first > last` are ignored.
    ///
    /// @param first inclusive lower bound
    /// @param last inclusive upper bound
    public void remove(int first, int last) {
        if (first > last)
            return;

        int exclusiveEnd = last + 1;
        IntervalEntry firstAffectedEntry = findFirstEntryEndingAfter(first);
        if (firstAffectedEntry == null)
            return;

        IntervalEntry lastAffectedEntry = findLastEntryStartingBefore(exclusiveEnd);
        if (lastAffectedEntry == null)
            return;
        if (firstAffectedEntry != lastAffectedEntry
                && lastAffectedEntry.exclusiveEnd <= firstAffectedEntry.first)
            return;

        if (firstAffectedEntry == lastAffectedEntry) {
            removeFromSingleEntry(firstAffectedEntry, first, exclusiveEnd);
            return;
        }

        boolean keepLeftFragment = firstAffectedEntry.first < first;
        boolean keepRightFragment = lastAffectedEntry.exclusiveEnd > exclusiveEnd;

        modCount++;
        IntervalEntry afterAffectedEntries = nextEntry(lastAffectedEntry);
        IntervalEntry firstDeletedEntry = keepLeftFragment ? nextEntry(firstAffectedEntry) : firstAffectedEntry;
        IntervalEntry afterDeletedEntries = keepRightFragment ? lastAffectedEntry : afterAffectedEntries;

        if (keepLeftFragment)
            firstAffectedEntry.exclusiveEnd = first;
        if (keepRightFragment)
            lastAffectedEntry.first = exclusiveEnd;

        deleteEntries(firstDeletedEntry, afterDeletedEntries);
    }

    private void removeFromSingleEntry(IntervalEntry entry, int first, int exclusiveEnd) {
        if (first <= entry.first) {
            if (exclusiveEnd >= entry.exclusiveEnd) {
                modCount++;
                tree.deleteEntry(entry);
            } else if (entry.first != exclusiveEnd) {
                modCount++;
                entry.first = exclusiveEnd;
            }
            return;
        }

        if (exclusiveEnd >= entry.exclusiveEnd) {
            if (entry.exclusiveEnd != first) {
                modCount++;
                entry.exclusiveEnd = first;
            }
            return;
        }

        modCount++;
        IntervalEntry tail = new IntervalEntry(exclusiveEnd, entry.exclusiveEnd);
        entry.exclusiveEnd = first;
        insertEntryAfter(entry, tail);
    }

    /// Returns the number of disjoint intervals currently stored in the set.
    ///
    /// This is the number of coalesced spans, not the number of covered integer indexes.
    public int size() {
        return tree.getSize();
    }
}
