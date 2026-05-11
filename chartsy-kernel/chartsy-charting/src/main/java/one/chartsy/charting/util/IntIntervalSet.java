package one.chartsy.charting.util;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import one.chartsy.charting.util.collections.BalancedBinaryTree;

/// Stores disjoint inclusive integer intervals and coalesces overlaps or direct
/// adjacencies.
///
/// The set is optimized for charting update paths that repeatedly add, subtract, and
/// iterate changed index ranges. Stored intervals keep both endpoints inclusive, and
/// adjacency checks handle `Integer.MIN_VALUE` and `Integer.MAX_VALUE` explicitly.
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
    private static final class IntervalEntry extends BalancedBinaryTree.Entry implements IntInterval {
        int first;
        int lastInclusive;

        IntervalEntry(int first, int lastInclusive) {
            this.first = first;
            this.lastInclusive = lastInclusive;
        }

        @Override
        public int getFirst() {
            return first;
        }

        @Override
        public int getLast() {
            return lastInclusive;
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

        int lastInclusive = entry.lastInclusive;
        int low = 0;
        int high = entryCount - 1;
        while (low <= high) {
            int mid = low + ((high - low) >> 1);
            if (entryAt(node, mid).lastInclusive >= lastInclusive)
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

    private static int findFirstIndexWithLastAtLeast(BalancedBinaryTree.Node node, int value) {
        int low = 0;
        int high = node.getEntriesCount() - 1;
        while (low < high) {
            int mid = low + ((high - low) >> 1);
            if (entryAt(node, mid).lastInclusive >= value)
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

    private IntervalEntry firstEntry() {
        return (tree.getSize() == 0) ? null : (IntervalEntry) tree.getEntryAt(0);
    }

    private IntervalEntry findFirstEntryEndingAtOrAfter(int value) {
        BalancedBinaryTree.Node node = tree.getRoot();
        IntervalEntry candidate = null;
        while (node != null) {
            if (lastEntryOf(node).lastInclusive < value) {
                node = node.getRightBranch();
                continue;
            }

            int index = findFirstIndexWithLastAtLeast(node, value);
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

    private static boolean hasUncoveredValueBetween(IntervalEntry previousEntry, IntervalEntry nextEntry) {
        return previousEntry.lastInclusive != Integer.MAX_VALUE
                && previousEntry.lastInclusive + 1 < nextEntry.first;
    }

    private static int predecessorOrSelf(int value) {
        return (value == Integer.MIN_VALUE) ? Integer.MIN_VALUE : value - 1;
    }

    private static int successorOrSelf(int value) {
        return (value == Integer.MAX_VALUE) ? Integer.MAX_VALUE : value + 1;
    }

    /// Looks one value to the left so adjacency merges with an interval ending at `first - 1`.
    ///
    /// The lookup clamps at `Integer.MIN_VALUE` because there is no representable predecessor.
    private IntervalEntry findFirstEntryEndingAtOrAfterOrAdjacent(int first) {
        return findFirstEntryEndingAtOrAfter(predecessorOrSelf(first));
    }

    /// Looks one value to the right so adjacency merges with an interval starting at `last + 1`.
    ///
    /// The lookup clamps at `Integer.MAX_VALUE` because there is no representable successor.
    private IntervalEntry findLastEntryStartingAtOrBeforeOrAdjacent(int last) {
        return findLastEntryStartingAtOrBefore(successorOrSelf(last));
    }

    private void appendRange(int first, int last) {
        modCount++;
        appendEntry(new IntervalEntry(first, last));
    }

    private void prependRange(int first, int last) {
        modCount++;
        prependEntry(new IntervalEntry(first, last));
    }

    private void insertDisjointRange(IntervalEntry previousEntry, IntervalEntry nextEntry, int first, int last) {
        modCount++;
        insertEntryBetween(previousEntry, nextEntry, new IntervalEntry(first, last));
    }

    private static boolean rangeFallsBetweenCandidates(int first, IntervalEntry leftCandidate, IntervalEntry rightCandidate) {
        return rightCandidate.first > first && hasUncoveredValueBetween(leftCandidate, rightCandidate);
    }

    private static boolean mergedRangeChangesStoredEntries(IntervalEntry firstMergedEntry, IntervalEntry lastMergedEntry,
                                                           int mergedFirst, int mergedLastInclusive) {
        return firstMergedEntry != lastMergedEntry
                || mergedFirst != firstMergedEntry.first
                || mergedLastInclusive != firstMergedEntry.lastInclusive;
    }

    private int deleteMergedFollowers(IntervalEntry firstMergedEntry, IntervalEntry lastMergedEntry,
                                      int mergedLastInclusive) {
        IntervalEntry afterMergedEntries = nextEntry(lastMergedEntry);
        IntervalEntry current = nextEntry(firstMergedEntry);
        while (current != null && current != afterMergedEntries) {
            mergedLastInclusive = Math.max(mergedLastInclusive, current.lastInclusive);
            IntervalEntry next = nextEntry(current);
            tree.deleteEntry(current);
            current = next;
        }
        return mergedLastInclusive;
    }

    private void mergeRangeIntoStoredEntries(IntervalEntry firstMergedEntry, IntervalEntry lastMergedEntry,
                                             int first, int last) {
        int mergedFirst = Math.min(first, firstMergedEntry.first);
        int mergedLastInclusive = Math.max(last, firstMergedEntry.lastInclusive);
        if (!mergedRangeChangesStoredEntries(firstMergedEntry, lastMergedEntry, mergedFirst, mergedLastInclusive))
            return;

        modCount++;
        if (firstMergedEntry != lastMergedEntry)
            mergedLastInclusive = deleteMergedFollowers(firstMergedEntry, lastMergedEntry, mergedLastInclusive);

        firstMergedEntry.first = mergedFirst;
        firstMergedEntry.lastInclusive = mergedLastInclusive;
    }

    /// Adds an inclusive interval to the set.
    ///
    /// Overlapping or directly adjacent stored intervals are merged into one interval.
    /// Calls with `first > last` are ignored.
    ///
    /// @param first inclusive lower bound
    /// @param last  inclusive upper bound
    public void add(int first, int last) {
        if (first > last)
            return;

        IntervalEntry firstCandidateEntry = findFirstEntryEndingAtOrAfterOrAdjacent(first);
        if (firstCandidateEntry == null) {
            appendRange(first, last);
            return;
        }

        IntervalEntry lastCandidateEntry = findLastEntryStartingAtOrBeforeOrAdjacent(last);
        if (lastCandidateEntry == null) {
            prependRange(first, last);
            return;
        }

        if (rangeFallsBetweenCandidates(first, lastCandidateEntry, firstCandidateEntry)) {
            insertDisjointRange(lastCandidateEntry, firstCandidateEntry, first, last);
            return;
        }

        mergeRangeIntoStoredEntries(firstCandidateEntry, lastCandidateEntry, first, last);
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
        IntervalEntry candidate = findFirstEntryEndingAtOrAfter(value);
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
    /// @param last  inclusive upper bound
    public void remove(int first, int last) {
        if (first > last)
            return;

        IntervalEntry firstAffectedEntry = findFirstEntryEndingAtOrAfter(first);
        if (firstAffectedEntry == null)
            return;

        IntervalEntry lastAffectedEntry = findLastEntryStartingAtOrBefore(last);
        if (lastAffectedEntry == null)
            return;
        if (removalFallsBetweenStoredEntries(firstAffectedEntry, lastAffectedEntry))
            return;

        if (firstAffectedEntry == lastAffectedEntry) {
            removeWithinSingleEntry(firstAffectedEntry, first, last);
            return;
        }

        removeAcrossAffectedEntries(firstAffectedEntry, lastAffectedEntry, first, last);
    }

    private static boolean removalFallsBetweenStoredEntries(IntervalEntry firstAffectedEntry,
                                                            IntervalEntry lastAffectedEntry) {
        return firstAffectedEntry != lastAffectedEntry
                && lastAffectedEntry.lastInclusive < firstAffectedEntry.first;
    }

    private void removeAcrossAffectedEntries(IntervalEntry firstAffectedEntry, IntervalEntry lastAffectedEntry,
                                             int first, int last) {
        boolean keepLeftFragment = firstAffectedEntry.first < first;
        boolean keepRightFragment = lastAffectedEntry.lastInclusive > last;

        modCount++;
        IntervalEntry afterAffectedEntries = nextEntry(lastAffectedEntry);
        IntervalEntry firstDeletedEntry = keepLeftFragment ? nextEntry(firstAffectedEntry) : firstAffectedEntry;
        IntervalEntry afterDeletedEntries = keepRightFragment ? lastAffectedEntry : afterAffectedEntries;

        if (keepLeftFragment)
            firstAffectedEntry.lastInclusive = first - 1;
        if (keepRightFragment)
            lastAffectedEntry.first = last + 1;

        deleteEntries(firstDeletedEntry, afterDeletedEntries);
    }

    private void removeWithinSingleEntry(IntervalEntry entry, int first, int last) {
        if (first <= entry.first) {
            removeFromEntryStart(entry, last);
            return;
        }

        if (last >= entry.lastInclusive) {
            removeFromEntryEnd(entry, first);
            return;
        }

        splitEntryAroundRemovedRange(entry, first, last);
    }

    private void removeFromEntryStart(IntervalEntry entry, int last) {
        if (last >= entry.lastInclusive) {
            modCount++;
            tree.deleteEntry(entry);
        } else if (entry.first != last + 1) {
            modCount++;
            entry.first = last + 1;
        }
    }

    private void removeFromEntryEnd(IntervalEntry entry, int first) {
        if (entry.lastInclusive != first - 1) {
            modCount++;
            entry.lastInclusive = first - 1;
        }
    }

    private void splitEntryAroundRemovedRange(IntervalEntry entry, int first, int last) {
        modCount++;
        IntervalEntry rightFragment = new IntervalEntry(last + 1, entry.lastInclusive);
        entry.lastInclusive = first - 1;
        insertEntryAfter(entry, rightFragment);
    }

    /// Returns the number of disjoint intervals currently stored in the set.
    ///
    /// This is the number of coalesced spans, not the number of covered integer indexes.
    public int size() {
        return tree.getSize();
    }
}
