package one.chartsy.charting.util;

import java.util.AbstractSet;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/// Maps primitive `double` keys to values while avoiding boxing during lookup and update paths.
///
/// This implementation backs renderer helpers that need sorted keyed storage with stable iteration
/// order. Internally it stores up to two entries per tree node and keeps the structure balanced
/// through red-black-style rotations. The constructor argument controls only traversal direction for
/// [#entrySet()]: `-1` iterates entries in descending key order, and any other value iterates in
/// ascending order.
///
/// `null` values are allowed, so callers should use [#containsKey(double)] to distinguish an absent
/// key from a present key whose value is `null`. [#getEntry(double)] exposes the live stored entry
/// object rather than a snapshot, and [#clone()] performs a structural copy while reusing the same
/// value references.
public class DoubleTreeMap<V extends Object> implements Cloneable {

    /// Shared fail-fast state for iterators that walk the map one stored entry at a time.
    ///
    /// The iterator traverses the threaded node chain rather than rebuilding a sorted snapshot, so
    /// subclasses only need to choose whether iteration moves toward larger or smaller keys.
    static class AbstractMapIterator<V extends Object> {
        DoubleTreeMap<V> a;
        int b;
        DoubleTreeMap.Node<V> c;
        DoubleTreeMap.Node<V> d;
        int e;
        int f;

        AbstractMapIterator(DoubleTreeMap<V> map) {
            this(map, (map.b != -1) ? leftmost(map.e) : rightmost(map.e));
        }

        AbstractMapIterator(DoubleTreeMap<V> map, DoubleTreeMap.Node<V> startNode) {
            this(map, startNode, (startNode == null) ? 0 : startNode.h - startNode.firstEntryIndex);
        }

        AbstractMapIterator(DoubleTreeMap<V> map, DoubleTreeMap.Node<V> startNode, int startOffset) {
            this.a = map;
            this.b = map.c;
            this.c = startNode;
            this.e = startOffset;
        }

        final void advanceAscending() {
            if (this.b != this.a.c)
                throw new ConcurrentModificationException();
            if (this.c == null)
                throw new NoSuchElementException();
            this.d = this.c;
            this.f = this.e;
            if (this.e != 0) {
                this.e--;
            } else {
                this.c = this.c.c;
                if (this.c != null)
                    this.e = this.c.h - this.c.firstEntryIndex;
            }
        }

        final void advanceDescending() {
            if (this.b != this.a.c)
                throw new ConcurrentModificationException();
            if (this.c == null)
                throw new NoSuchElementException();
            this.d = this.c;
            this.f = this.e;
            if (this.e != 0) {
                this.e--;
            } else {
                this.c = this.c.b;
                if (this.c != null)
                    this.e = this.c.h - this.c.firstEntryIndex;
            }
        }

        public boolean hasNext() {
            return this.c != null;
        }

        /// Removes the last entry returned by this iterator.
        ///
        /// The call is fail-fast with respect to concurrent structural modification and may be
        /// invoked at most once after each successful `next()`.
        public final void remove() {
            if (this.b != this.a.c)
                throw new ConcurrentModificationException();
            if (this.d == null)
                throw new IllegalStateException();
            int entryIndex = this.d.h - this.f;
            this.a.removeEntry(this.d, entryIndex);
            this.d = null;
            this.b++;
        }
    }

    /// Live key/value pair stored directly inside the tree.
    ///
    /// The entry is retained by the map, so callers should treat it as a view of the current
    /// stored state rather than as an owned snapshot.
    public static final class Entry<V extends Object> {
        double a;
        V b;

        /// Creates an entry for a specific key and value.
        public Entry(double key, V value) {
            this.b = value;
            this.a = key;
        }

        /// Returns the stored primitive key.
        public double getKey() {
            return this.a;
        }

        /// Returns the stored value reference, which may be `null`.
        public V getValue() {
            return this.b;
        }
    }

    /// Internal fixed-capacity tree node used by balancing, cloning, and iteration code.
    ///
    /// Each node stores one or two live entries plus explicit predecessor and successor links so
    /// ordered traversal can move between nodes without re-searching from the root.
    static class Node<V extends Object> implements Cloneable {
        static final int a = 2;
        DoubleTreeMap.Node<V> b;
        DoubleTreeMap.Node<V> c;
        DoubleTreeMap.Node<V> d;
        DoubleTreeMap.Node<V> e;
        DoubleTreeMap.Node<V> f;
        int firstEntryIndex;
        int h;
        int i;
        boolean j;
        DoubleTreeMap.Entry<V>[] k;

        public Node() {
            this.firstEntryIndex = 0;
            this.h = -1;
            this.i = 0;
            this.k = new DoubleTreeMap.Entry[2];
        }

        /// Clones this node and its child subtree while preserving the in-node entry range.
        ///
        /// Threaded predecessor and successor links are rebuilt separately by [DoubleTreeMap#clone()],
        /// so this helper intentionally clears them in the cloned subtree.
        DoubleTreeMap.Node<V> cloneSubtree(DoubleTreeMap.Node<V> parent) {
            DoubleTreeMap.Node<V> clonedNode = clone();
            clonedNode.k = new DoubleTreeMap.Entry[2];
            System.arraycopy(this.k, 0, clonedNode.k, 0, this.k.length);
            clonedNode.firstEntryIndex = this.firstEntryIndex;
            clonedNode.h = this.h;
            clonedNode.d = parent;
            if (this.e != null)
                clonedNode.e = this.e.cloneSubtree(clonedNode);
            if (this.f != null)
                clonedNode.f = this.f.cloneSubtree(clonedNode);
            clonedNode.b = null;
            clonedNode.c = null;
            return clonedNode;
        }

        @Override
        public Node clone() {
            try {
                return (Node) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new InternalError("shouldn't happen");
            }
        }
    }

    /// Iterator over the entire map in the direction selected by the owning map.
    static class UnboundedEntryIterator<V extends Object> extends DoubleTreeMap.AbstractMapIterator<V>
            implements Iterator<DoubleTreeMap.Entry<V>> {

        UnboundedEntryIterator(DoubleTreeMap<V> map) {
            super(map);
        }

        UnboundedEntryIterator(DoubleTreeMap<V> map, DoubleTreeMap.Node<V> startNode, int startOffset) {
            super(map, startNode, startOffset);
        }

        /// Returns the next live entry in key order.
        @Override
        public DoubleTreeMap.Entry<V> next() {
            if (super.a.b != -1)
                super.advanceAscending();
            else
                super.advanceDescending();
            int entryIndex = (super.a.b != -1) ? super.d.h - super.f : super.f + super.d.firstEntryIndex;
            return super.d.k[entryIndex];
        }
    }

    static <V extends Object> DoubleTreeMap.Node<V> leftmost(DoubleTreeMap.Node<V> node) {
        DoubleTreeMap.Node<V> current = node;
        if (current == null)
            return null;
        while (current.e != null)
            current = current.e;
        return current;
    }

    static <V extends Object> DoubleTreeMap.Node<V> rightmost(DoubleTreeMap.Node<V> node) {
        DoubleTreeMap.Node<V> current = node;
        if (current == null)
            return null;
        while (current.f != null)
            current = current.f;
        return current;
    }

    private static <V extends Object> DoubleTreeMap.Node<V> successor(DoubleTreeMap.Node<V> node) {
        DoubleTreeMap.Node<V> current = node;
        if (current.f != null)
            return leftmost(current.f);
        DoubleTreeMap.Node<V> parent = current.d;
        while (parent != null && current == parent.f) {
            current = parent;
            parent = parent.d;
        }
        return parent;
    }

    transient int a;

    private final int b;

    transient int c;

    transient Set<DoubleTreeMap.Entry<V>> d;

    transient DoubleTreeMap.Node<V> e;

    /// Creates a map whose entry-set iterators run in ascending order unless `direction` is `-1`.
    public DoubleTreeMap(int direction) {
        this.b = direction;
    }

    int getIterationDirection() {
        return this.b;
    }

    private int compareKeys(double leftKey, double rightKey) {
        if (leftKey > rightKey)
            return 1;
        if (leftKey == rightKey)
            return 0;
        return -1;
    }

    /// Creates a fresh one-entry node for a primitive key/value pair.
    private DoubleTreeMap.Node<V> createNode(double key, V value) {
        DoubleTreeMap.Node<V> node = new DoubleTreeMap.Node<>();
        DoubleTreeMap.Entry<V> entry = new DoubleTreeMap.Entry<>(key, value);
        node.k[0] = entry;
        node.firstEntryIndex = 0;
        node.h = 0;
        node.i = 1;
        return node;
    }

    /// Wraps an already prepared entry in a fresh one-entry node.
    private DoubleTreeMap.Node<V> createNode(DoubleTreeMap.Entry<V> entry) {
        DoubleTreeMap.Node<V> node = new DoubleTreeMap.Node<>();
        node.k[0] = entry;
        node.firstEntryIndex = 0;
        node.h = 0;
        node.i = 1;
        return node;
    }

    /// Inserts a key known to belong after `node`, splitting into a new right child when needed.
    DoubleTreeMap.Node<V> insertAfterNode(DoubleTreeMap.Node<V> node, double key, V value) {
        DoubleTreeMap.Node<V> insertionNode = node;
        if (insertionNode == null) {
            DoubleTreeMap.Node<V> createdNode = this.createNode(key, value);
            insertionNode = createdNode;
            this.e = createdNode;
            this.a = 1;
        } else if (insertionNode.i != 2) {
            this.appendEntry(insertionNode, key, value);
            this.a++;
        } else {
            DoubleTreeMap.Node<V> createdNode = this.createNode(key, value);
            this.linkRightChild(insertionNode, createdNode);
            this.fixAfterInsertion(createdNode);
            this.a++;
            insertionNode = createdNode;
        }
        return insertionNode;
    }

    /// Prepends `entry` to the lower-key side of `node`.
    private void prependEntry(DoubleTreeMap.Node<V> node, DoubleTreeMap.Entry<V> entry) {
        if (node.firstEntryIndex != 0) {
            node.firstEntryIndex--;
        } else {
            int occupiedEntryCount = node.h + 1;
            System.arraycopy(node.k, 0, node.k, 1, occupiedEntryCount);
            node.h = occupiedEntryCount;
        }
        node.i++;
        node.k[node.firstEntryIndex] = entry;
    }

    /// Attaches `leftChild` as the structural left child and threaded predecessor of `parent`.
    private void linkLeftChild(DoubleTreeMap.Node<V> parent, DoubleTreeMap.Node<V> leftChild) {
        leftChild.d = parent;
        parent.e = leftChild;
        DoubleTreeMap.Node<V> predecessor = parent.b;
        leftChild.b = predecessor;
        leftChild.c = parent;
        if (predecessor != null)
            predecessor.c = leftChild;
        parent.b = leftChild;
    }

    /// Removes an interior entry from a two-entry node after callers have ruled out boundary slots.
    void removeMiddleEntry(DoubleTreeMap.Node<V> node, int entryIndex) {
        if (node.b != null && 1 - node.b.h > node.i) {
            DoubleTreeMap.Node<V> predecessor = node.b;
            int firstIndex = node.firstEntryIndex;
            int leftEntryCount = entryIndex - firstIndex;
            System.arraycopy(node.k, firstIndex, predecessor.k, predecessor.h + 1, leftEntryCount);
            predecessor.h += leftEntryCount;
            int rightEntryCount = node.h - entryIndex;
            System.arraycopy(node.k, entryIndex + 1, predecessor.k, predecessor.h + 1, rightEntryCount);
            predecessor.h += rightEntryCount;
            predecessor.i += node.i - 1;
            this.removeNode(node);
        } else if (node.c != null && node.c.firstEntryIndex > node.i) {
            DoubleTreeMap.Node<V> successor = node.c;
            int firstIndex = node.firstEntryIndex;
            int successorInsertIndex = successor.firstEntryIndex - node.i + 1;
            successor.firstEntryIndex = successorInsertIndex;
            int leftEntryCount = entryIndex - firstIndex;
            System.arraycopy(node.k, firstIndex, successor.k, successorInsertIndex, leftEntryCount);
            successorInsertIndex += leftEntryCount;
            int rightEntryCount = node.h - entryIndex;
            System.arraycopy(node.k, entryIndex + 1, successor.k, successorInsertIndex, rightEntryCount);
            successor.i += node.i - 1;
            this.removeNode(node);
        } else {
            int entriesAfter = node.h - entryIndex;
            int firstIndex = node.firstEntryIndex;
            int entriesBefore = entryIndex - firstIndex;
            if (entriesAfter > entriesBefore) {
                System.arraycopy(node.k, firstIndex, node.k, firstIndex + 1, entriesBefore);
                DoubleTreeMap.Node<V> predecessor = node.b;
                if (predecessor != null && predecessor.i == 1) {
                    node.k[firstIndex] = predecessor.k[predecessor.firstEntryIndex];
                    this.removeNode(predecessor);
                } else {
                    node.k[firstIndex] = new DoubleTreeMap.Entry<>(-1.0, null);
                    node.firstEntryIndex++;
                    node.i--;
                }
            } else {
                System.arraycopy(node.k, entryIndex + 1, node.k, entryIndex, entriesAfter);
                DoubleTreeMap.Node<V> successor = node.c;
                if (successor != null && successor.i == 1) {
                    node.k[node.h] = successor.k[successor.firstEntryIndex];
                    this.removeNode(successor);
                } else {
                    node.k[node.h] = new DoubleTreeMap.Entry<>(-1.0, null);
                    node.h--;
                    node.i--;
                }
            }
        }
        this.c++;
        this.a--;
    }

    /// Prepends a primitive key/value pair to the lower-key side of `node`.
    private void prependEntry(DoubleTreeMap.Node<V> node, double key, V value) {
        DoubleTreeMap.Entry<V> entry = new DoubleTreeMap.Entry<>(key, value);
        this.prependEntry(node, entry);
    }

    /// Appends `entry` to the higher-key side of `node`.
    private void appendEntry(DoubleTreeMap.Node<V> node, DoubleTreeMap.Entry<V> entry) {
        if (node.h != 1) {
            node.h++;
        } else {
            int firstIndex = node.firstEntryIndex;
            int newFirstIndex = firstIndex - 1;
            System.arraycopy(node.k, firstIndex, node.k, newFirstIndex, 2 - firstIndex);
            node.firstEntryIndex = newFirstIndex;
        }
        node.i++;
        node.k[node.h] = entry;
    }

    /// Attaches `rightChild` as the structural right child and threaded successor of `parent`.
    private void linkRightChild(DoubleTreeMap.Node<V> parent, DoubleTreeMap.Node<V> rightChild) {
        rightChild.d = parent;
        parent.f = rightChild;
        rightChild.b = parent;
        DoubleTreeMap.Node<V> successor = parent.c;
        rightChild.c = successor;
        if (successor != null)
            successor.b = rightChild;
        parent.c = rightChild;
    }

    /// Removes the entry at `entryIndex`, using cheaper boundary-specialized paths when possible.
    void removeEntry(DoubleTreeMap.Node<V> node, int entryIndex) {
        if (node.i == 1)
            this.removeNode(node);
        else if (entryIndex == node.firstEntryIndex) {
            DoubleTreeMap.Node<V> predecessor = node.b;
            if (predecessor != null && predecessor.i == 1) {
                node.k[entryIndex] = predecessor.k[predecessor.firstEntryIndex];
                this.removeNode(predecessor);
            } else {
                node.k[entryIndex] = new DoubleTreeMap.Entry<>(-1.0, null);
                node.firstEntryIndex++;
                node.i--;
            }
        } else if (entryIndex == node.h) {
            node.k[entryIndex] = new DoubleTreeMap.Entry<>(-1.0, null);
            node.h--;
            node.i--;
        } else {
            int entriesAfter = node.h - entryIndex;
            int entriesBefore = entryIndex - node.firstEntryIndex;
            if (entriesAfter > entriesBefore) {
                System.arraycopy(node.k, node.firstEntryIndex, node.k, node.firstEntryIndex + 1, entriesBefore);
                node.k[node.firstEntryIndex] = new DoubleTreeMap.Entry<>(-1.0, null);
                node.firstEntryIndex++;
                node.i--;
            } else {
                System.arraycopy(node.k, entryIndex + 1, node.k, entryIndex, entriesAfter);
                node.k[node.h] = new DoubleTreeMap.Entry<>(-1.0, null);
                node.h--;
                node.i--;
            }
        }
        this.c++;
        this.a--;
    }

    /// Restores the red-black invariants after linking a new red node into the tree.
    ///
    /// The threaded predecessor/successor links are already in place when this method runs, so the
    /// fix-up only adjusts colors and subtree shape.
    void fixAfterInsertion(DoubleTreeMap.Node<V> node) {
        DoubleTreeMap.Node<V> current = node;
        current.j = true;
        while (true) {
            if (current == this.e)
                break;
            if (!current.d.j)
                break;
            DoubleTreeMap.Node<V> uncle;
            if (current.d == current.d.d.e) {
                uncle = current.d.d.f;
                if (uncle != null)
                    if (uncle.j) {
                        current.d.j = false;
                        uncle.j = false;
                        current.d.d.j = true;
                        current = current.d.d;
                        continue;
                    }
                if (current == current.d.f) {
                    current = current.d;
                    this.rotateLeft(current);
                }
                current.d.j = false;
                current.d.d.j = true;
                this.rotateRight(current.d.d);
            } else {
                uncle = current.d.d.e;
                if (uncle != null)
                    if (uncle.j) {
                        current.d.j = false;
                        uncle.j = false;
                        current.d.d.j = true;
                        current = current.d.d;
                        continue;
                    }
                if (current == current.d.e) {
                    current = current.d;
                    this.rotateRight(current);
                }
                current.d.j = false;
                current.d.d.j = true;
                this.rotateLeft(current.d.d);
            }
        }
        this.e.j = false;
    }

    /// Appends a primitive key/value pair to the higher-key side of `node`.
    private void appendEntry(DoubleTreeMap.Node<V> node, double key, V value) {
        DoubleTreeMap.Entry<V> entry = new DoubleTreeMap.Entry<>(key, value);
        this.appendEntry(node, entry);
    }

    /// Replaces `node` in the tree with the non-null `replacement` and repairs deletion balance
    /// when the removed node was black.
    private void replaceNodeAndRebalance(DoubleTreeMap.Node<V> node, DoubleTreeMap.Node<V> replacement) {
        this.replaceInParent(node, replacement);
        if (!node.j)
            this.fixAfterDeletion(replacement);
    }

    /// Removes every entry from the map.
    ///
    /// Any previously obtained entries or iterators become stale after this call.
    public void clear() {
        this.e = null;
        this.a = 0;
        this.c++;
    }

    /// Returns a structural copy of the map with the same iteration direction and value references.
    ///
    /// The copy rebuilds the threaded predecessor and successor chain over cloned nodes, but it
    /// still reuses the original value references stored in each entry.
    @Override
    public Object clone() {
        try {
            DoubleTreeMap<V> clonedMap = (DoubleTreeMap<V>) super.clone();
            clonedMap.d = null;
            if (this.e != null) {
                clonedMap.e = this.e.cloneSubtree(null);
                DoubleTreeMap.Node<V> currentNode = leftmost(clonedMap.e);
                while (true) {
                    DoubleTreeMap.Node<V> nextNode = successor(currentNode);
                    if (nextNode == null)
                        break;
                    nextNode.b = currentNode;
                    currentNode.c = nextNode;
                    currentNode = nextNode;
                }
            }
            return clonedMap;
        } catch (CloneNotSupportedException e1) {
            return null;
        }
    }

    /// Returns `true` when the map currently stores an entry for `key`.
    public boolean containsKey(double key) {
        DoubleTreeMap.Node<V> currentNode = this.e;
        while (currentNode != null) {
            DoubleTreeMap.Entry<V>[] entries = currentNode.k;
            int firstIndex = currentNode.firstEntryIndex;
            int comparison = this.compareKeys(key, entries[firstIndex].a);
            if (comparison < 0) {
                currentNode = currentNode.e;
                continue;
            }
            if (comparison == 0)
                return true;
            int lastIndex = currentNode.h;
            if (firstIndex != lastIndex)
                comparison = this.compareKeys(key, entries[lastIndex].a);
            if (comparison > 0) {
                currentNode = currentNode.f;
                continue;
            }
            if (comparison == 0)
                return true;
            int lowIndex = firstIndex + 1;
            int highIndex = lastIndex - 1;
            while (lowIndex <= highIndex) {
                int middleIndex = lowIndex + highIndex >> 1;
                comparison = this.compareKeys(key, entries[middleIndex].a);
                if (comparison > 0)
                    lowIndex = middleIndex + 1;
                else if (comparison < 0)
                    highIndex = middleIndex - 1;
                else
                    return true;
            }
            return false;
        }
        return false;
    }

    /// Removes the first stored entry from `node`.
    void removeFirstEntry(DoubleTreeMap.Node<V> node) {
        int firstIndex = node.firstEntryIndex;
        if (node.i == 1)
            this.removeNode(node);
        else if (node.b != null && 1 - node.b.h > node.i) {
            DoubleTreeMap.Node<V> predecessor = node.b;
            int transferredEntryCount = node.h - firstIndex;
            System.arraycopy(node.k, firstIndex + 1, predecessor.k, predecessor.h + 1, transferredEntryCount);
            predecessor.h += transferredEntryCount;
            predecessor.i += transferredEntryCount;
            this.removeNode(node);
        } else if (node.c != null && node.c.firstEntryIndex > node.i) {
            DoubleTreeMap.Node<V> successor = node.c;
            int transferredEntryCount = node.h - firstIndex;
            int successorInsertIndex = successor.firstEntryIndex - transferredEntryCount;
            successor.firstEntryIndex = successorInsertIndex;
            System.arraycopy(node.k, firstIndex + 1, successor.k, successorInsertIndex, transferredEntryCount);
            successor.i += transferredEntryCount;
            this.removeNode(node);
        } else {
            node.k[firstIndex] = new DoubleTreeMap.Entry<>(-1.0, null);
            node.firstEntryIndex++;
            node.i--;
            DoubleTreeMap.Node<V> predecessor = node.b;
            if (predecessor != null && predecessor.i == 1) {
                node.i++;
                node.firstEntryIndex--;
                node.k[node.firstEntryIndex] = predecessor.k[predecessor.firstEntryIndex];
                this.removeNode(predecessor);
            }
        }
        this.c++;
        this.a--;
    }

    /// Replaces `node` in its parent slot with `replacement`, updating the root reference when
    /// necessary.
    private void replaceInParent(DoubleTreeMap.Node<V> node, DoubleTreeMap.Node<V> replacement) {
        DoubleTreeMap.Node<V> parent = node.d;
        replacement.d = parent;
        if (parent == null)
            this.e = replacement;
        else if (node != parent.e)
            parent.f = replacement;
        else
            parent.e = replacement;
    }

    /// Removes the last stored entry from `node`.
    void removeLastEntry(DoubleTreeMap.Node<V> node) {
        int lastIndex = node.h;
        if (node.i == 1)
            this.removeNode(node);
        else if (node.b != null && 1 - node.b.h > node.i) {
            DoubleTreeMap.Node<V> predecessor = node.b;
            int transferredEntryCount = lastIndex - node.firstEntryIndex;
            System.arraycopy(node.k, node.firstEntryIndex, predecessor.k, predecessor.h + 1, transferredEntryCount);
            predecessor.h += transferredEntryCount;
            predecessor.i += transferredEntryCount;
            this.removeNode(node);
        } else if (node.c != null && node.c.firstEntryIndex > node.i) {
            DoubleTreeMap.Node<V> successor = node.c;
            int transferredEntryCount = lastIndex - node.firstEntryIndex;
            int successorInsertIndex = successor.firstEntryIndex - transferredEntryCount;
            successor.firstEntryIndex = successorInsertIndex;
            System.arraycopy(node.k, node.firstEntryIndex, successor.k, successorInsertIndex, transferredEntryCount);
            successor.i += transferredEntryCount;
            this.removeNode(node);
        } else {
            node.k[lastIndex] = new DoubleTreeMap.Entry<>(-1.0, null);
            node.h--;
            node.i--;
            DoubleTreeMap.Node<V> successor = node.c;
            if (successor != null && successor.i == 1) {
                node.i++;
                node.h++;
                node.k[node.h] = successor.k[successor.firstEntryIndex];
                this.removeNode(successor);
            }
        }
        this.c++;
        this.a--;
    }

    /// Returns a live entry view backed by this map.
    ///
    /// Iteration order follows the direction chosen at construction time. The returned set does
    /// not snapshot the map: removals act on the underlying tree immediately, and later map
    /// updates become visible through the same view instance.
    public Set<DoubleTreeMap.Entry<V>> entrySet() {
        if (this.d == null)
            this.d = new AbstractSet<DoubleTreeMap.Entry<V>>() {
                /* synthetic */ final DoubleTreeMap a = DoubleTreeMap.this;

                @Override
                public void clear() {
                    this.a.clear();
                }

                @Override
                public boolean contains(Object candidate) {
                    if (!(candidate instanceof DoubleTreeMap.Entry<?> entry))
                        return false;
                    Object storedValue = this.a.get(entry.getKey());
                    Object candidateValue = entry.getValue();
                    return (storedValue != null) ? storedValue.equals(candidateValue) : candidateValue == null;
                }

                @Override
                public Iterator<DoubleTreeMap.Entry<V>> iterator() {
                    return new DoubleTreeMap.UnboundedEntryIterator(this.a);
                }

                @Override
                public boolean remove(Object candidate) {
                    if (!this.contains(candidate))
                        return false;
                    DoubleTreeMap.Entry<?> entry = (DoubleTreeMap.Entry<?>) candidate;
                    this.a.remove(entry.getKey());
                    return true;
                }

                @Override
                public int size() {
                    return this.a.a;
                }
            };
        return this.d;
    }

    /// Rotates the subtree rooted at `node` one step to the right.
    ///
    /// This updates tree parent/root ownership and child pointers only. The threaded predecessor and
    /// successor links stay valid because in-order node order does not change.
    private void rotateRight(DoubleTreeMap.Node<V> node) {
        DoubleTreeMap.Node<V> pivot = node.e;
        node.e = pivot.f;
        if (pivot.f != null)
            pivot.f.d = node;
        pivot.d = node.d;
        if (node.d == null)
            this.e = pivot;
        else if (node != node.d.f)
            node.d.e = pivot;
        else
            node.d.f = pivot;
        pivot.f = node;
        node.d = pivot;
    }

    /// Returns the value currently stored for `key`, or `null` when the key is absent.
    ///
    /// Because `null` values are permitted, use [#containsKey(double)] to distinguish absence from
    /// an explicit `null` mapping.
    public V get(double key) {
        DoubleTreeMap.Node<V> currentNode = this.e;
        while (currentNode != null) {
            DoubleTreeMap.Entry<V>[] entries = currentNode.k;
            int firstIndex = currentNode.firstEntryIndex;
            int comparison = this.compareKeys(key, entries[firstIndex].a);
            if (comparison < 0) {
                currentNode = currentNode.e;
                continue;
            }
            if (comparison == 0)
                return currentNode.k[firstIndex].b;
            int lastIndex = currentNode.h;
            if (firstIndex != lastIndex)
                comparison = this.compareKeys(key, entries[lastIndex].a);
            if (comparison > 0) {
                currentNode = currentNode.f;
                continue;
            }
            if (comparison == 0)
                return currentNode.k[lastIndex].b;
            int lowIndex = firstIndex + 1;
            int highIndex = lastIndex - 1;
            while (lowIndex <= highIndex) {
                int middleIndex = lowIndex + highIndex >> 1;
                comparison = this.compareKeys(key, entries[middleIndex].a);
                if (comparison > 0)
                    lowIndex = middleIndex + 1;
                else if (comparison < 0)
                    highIndex = middleIndex - 1;
                else
                    return currentNode.k[middleIndex].b;
            }
            return null;
        }
        return null;
    }

    /// Returns the live stored entry for `key`, or `null` when the key is absent.
    ///
    /// The returned entry is owned by the map; callers should treat it as read-only.
    public DoubleTreeMap.Entry<V> getEntry(double key) {
        if (this.a == 0)
            return null;
        DoubleTreeMap.Node<V> currentNode = this.e;
        while (currentNode != null) {
            DoubleTreeMap.Entry<V>[] entries = currentNode.k;
            int firstIndex = currentNode.firstEntryIndex;
            int comparison = this.compareKeys(key, entries[firstIndex].a);
            if (comparison < 0) {
                currentNode = currentNode.e;
                continue;
            }
            if (comparison == 0)
                return currentNode.k[firstIndex];
            int lastIndex = currentNode.h;
            if (firstIndex != lastIndex)
                comparison = this.compareKeys(key, entries[lastIndex].a);
            if (comparison > 0) {
                currentNode = currentNode.f;
                continue;
            }
            if (comparison == 0)
                return currentNode.k[lastIndex];
            int lowIndex = firstIndex + 1;
            int highIndex = lastIndex - 1;
            while (lowIndex <= highIndex) {
                int middleIndex = lowIndex + highIndex >> 1;
                comparison = this.compareKeys(key, entries[middleIndex].a);
                if (comparison > 0)
                    lowIndex = middleIndex + 1;
                else if (comparison < 0)
                    highIndex = middleIndex - 1;
                else
                    return currentNode.k[middleIndex];
            }
            return null;
        }
        return null;
    }

    /// Rotates the subtree rooted at `node` one step to the left.
    ///
    /// This is the mirror operation of [#rotateRight(Node)] and likewise leaves the threaded
    /// predecessor/successor chain untouched.
    private void rotateLeft(DoubleTreeMap.Node<V> node) {
        DoubleTreeMap.Node<V> pivot = node.f;
        node.f = pivot.e;
        if (pivot.e != null)
            pivot.e.d = node;
        pivot.d = node.d;
        if (node.d == null)
            this.e = pivot;
        else if (node != node.d.e)
            node.d.f = pivot;
        else
            node.d.e = pivot;
        pivot.e = node;
        node.d = pivot;
    }

    /// Removes the whole `node` from the tree after callers have decided the node itself, rather
    /// than just one in-node entry slot, must disappear.
    ///
    /// The method handles leaf, single-child, and two-child cases. In the two-child path it
    /// promotes the threaded successor and preserves the removed node's color on the replacement so
    /// deletion balancing remains correct.
    private void removeNode(DoubleTreeMap.Node<V> node) {
        if (node.f == null) {
            if (node.e == null)
                this.unlinkLeafNode(node);
            else
                this.replaceNodeAndRebalance(node, node.e);
            this.unlinkIterationLinks(node);
        } else if (node.e == null) {
            this.replaceNodeAndRebalance(node, node.f);
            this.unlinkIterationLinks(node);
        } else {
            DoubleTreeMap.Node<V> successor = node.c;
            this.unlinkIterationLinks(node);
            if (successor.f == null)
                this.unlinkLeafNode(successor);
            else
                this.replaceNodeAndRebalance(successor, successor.f);
            successor.e = node.e;
            if (node.e != null)
                node.e.d = successor;
            successor.f = node.f;
            if (node.f != null)
                node.f.d = successor;
            this.replaceInParent(node, successor);
            successor.j = node.j;
        }
    }

    /// Detaches `node` when it is already known to be a leaf, clearing the root when the tree
    /// becomes empty.
    ///
    /// Deletion rebalancing runs only when the removed leaf was black and there is still a parent
    /// subtree to repair.
    private void unlinkLeafNode(DoubleTreeMap.Node<V> node) {
        DoubleTreeMap.Node<V> parent = node.d;
        if (parent == null)
            this.e = null;
        else {
            if (node != parent.e)
                parent.f = null;
            else
                parent.e = null;
            if (!node.j)
                this.fixAfterDeletion(parent);
        }
    }

    /// Removes `node` from the threaded predecessor/successor chain without touching tree shape.
    private void unlinkIterationLinks(DoubleTreeMap.Node<V> node) {
        if (node.b != null)
            node.b.c = node.c;
        if (node.c != null)
            node.c.b = node.b;
    }

    /// Repairs the red-black invariants after deleting a black node.
    ///
    /// Callers invoke this after structural transplanting and threaded-link cleanup. The
    /// `rebalancingNode` is the surviving replacement node or parent-side position from which the
    /// extra black should be pushed upward.
    private void fixAfterDeletion(DoubleTreeMap.Node<V> node) {
        DoubleTreeMap.Node<V> current = node;
        while (true) {
            if (current == this.e)
                break;
            if (current.j)
                break;
            DoubleTreeMap.Node<V> sibling;
            if (current == current.d.e) {
                sibling = current.d.f;
                if (sibling == null)
                    current = current.d;
                else {
                    if (sibling.j) {
                        sibling.j = false;
                        current.d.j = true;
                        this.rotateLeft(current.d);
                        sibling = current.d.f;
                        if (sibling == null) {
                            current = current.d;
                            continue;
                        }
                    }
                    block_3:
                    {
                        if (sibling.e != null)
                            if (sibling.e.j)
                                break block_3;
                        if (sibling.f != null)
                            if (sibling.f.j)
                                break block_3;
                        sibling.j = true;
                        current = current.d;
                        continue;
                    } // end block_3

                    block_4:
                    {
                        if (sibling.f != null)
                            if (sibling.f.j)
                                break block_4;
                        sibling.e.j = false;
                        sibling.j = true;
                        this.rotateRight(sibling);
                        sibling = current.d.f;
                    } // end block_4

                    sibling.j = current.d.j;
                    current.d.j = false;
                    sibling.f.j = false;
                    this.rotateLeft(current.d);
                    current = this.e;
                }
            } else {
                sibling = current.d.e;
                if (sibling == null)
                    current = current.d;
                else {
                    if (sibling.j) {
                        sibling.j = false;
                        current.d.j = true;
                        this.rotateRight(current.d);
                        sibling = current.d.e;
                        if (sibling == null) {
                            current = current.d;
                            continue;
                        }
                    }
                    block:
                    {
                        if (sibling.e != null)
                            if (sibling.e.j)
                                break block;
                        if (sibling.f != null)
                            if (sibling.f.j)
                                break block;
                        sibling.j = true;
                        current = current.d;
                        continue;
                    } // end block

                    block_2:
                    {
                        if (sibling.e != null)
                            if (sibling.e.j)
                                break block_2;
                        sibling.f.j = false;
                        sibling.j = true;
                        this.rotateLeft(sibling);
                        sibling = current.d.e;
                    } // end block_2

                    sibling.j = current.d.j;
                    current.d.j = false;
                    sibling.e.j = false;
                    this.rotateRight(current.d);
                    current = this.e;
                }
            }
        }
        current.j = false;
    }

    /// Associates `value` with `key` and returns the previous value for that key.
    ///
    /// Inserting a new key returns `null`. Updating an existing key replaces only the stored value;
    /// the key's position in iteration order remains unchanged.
    public V put(double key, V value) {
        if (this.e == null) {
            this.e = this.createNode(key, value);
            this.a = 1;
            this.c++;
            return null;
        }
        DoubleTreeMap.Node<V> currentNode = this.e;
        DoubleTreeMap.Node<V> parentNode = null;
        int comparison = 0;
        while (true) {
            if (currentNode == null)
                break;
            parentNode = currentNode;
            DoubleTreeMap.Entry<V>[] entries = currentNode.k;
            int firstIndex = currentNode.firstEntryIndex;
            comparison = this.compareKeys(key, entries[firstIndex].a);
            if (comparison < 0) {
                currentNode = currentNode.e;
            } else {
                if (comparison == 0) {
                    V previousValue = currentNode.k[firstIndex].b;
                    currentNode.k[firstIndex].b = value;
                    return previousValue;
                }
                int lastIndex = currentNode.h;
                if (firstIndex != lastIndex)
                    comparison = this.compareKeys(key, entries[lastIndex].a);
                if (comparison > 0)
                    currentNode = currentNode.f;
                else {
                    if (comparison == 0) {
                        V previousValue = currentNode.k[lastIndex].b;
                        currentNode.k[lastIndex].b = value;
                        return previousValue;
                    }
                    int lowIndex = firstIndex + 1;
                    int highIndex = lastIndex - 1;
                    while (true) {
                        if (lowIndex > highIndex)
                            break;
                        int middleIndex = lowIndex + highIndex >> 1;
                        comparison = this.compareKeys(key, entries[middleIndex].a);
                        if (comparison > 0)
                            lowIndex = middleIndex + 1;
                        else if (comparison != 0)
                            highIndex = middleIndex - 1;
                        else {
                            V previousValue = currentNode.k[middleIndex].b;
                            currentNode.k[middleIndex].b = value;
                            return previousValue;
                        }
                    }
                    comparison = lowIndex;
                    break;
                }
            }
        }
        this.a++;
        this.c++;
        if (currentNode != null) {
            if (currentNode.i < 2) {
                int firstIndex = currentNode.firstEntryIndex;
                int lastIndex = currentNode.h;
                if (firstIndex != 0 && (lastIndex == 1 || lastIndex - comparison > comparison - firstIndex)) {
                    int newFirstIndex = firstIndex - 1;
                    System.arraycopy(currentNode.k, firstIndex, currentNode.k, newFirstIndex, comparison - firstIndex);
                    currentNode.firstEntryIndex = newFirstIndex;
                    currentNode.k[comparison - 1] = new DoubleTreeMap.Entry<>(key, value);
                } else {
                    int newLastIndex = lastIndex + 1;
                    System.arraycopy(currentNode.k, comparison, currentNode.k, comparison + 1, newLastIndex - comparison);
                    currentNode.h = newLastIndex;
                    currentNode.k[comparison] = new DoubleTreeMap.Entry<>(key, value);
                }
                currentNode.i++;
            } else {
                DoubleTreeMap.Node<V> predecessor = currentNode.b;
                DoubleTreeMap.Node<V> successor = currentNode.c;
                boolean spillLeft;
                boolean attachAsLeftChild = false;
                DoubleTreeMap.Node<V> attachmentParent = null;
                if (predecessor == null) {
                    if (successor != null && successor.i < 2) {
                        spillLeft = false;
                    } else {
                        spillLeft = true;
                        attachAsLeftChild = true;
                        attachmentParent = currentNode;
                    }
                } else if (successor != null) {
                    if (predecessor.i >= 2) {
                        if (successor.i < 2)
                            spillLeft = false;
                        else if (currentNode.f != null) {
                            attachmentParent = successor;
                            attachAsLeftChild = true;
                            spillLeft = false;
                        } else {
                            attachmentParent = currentNode;
                            spillLeft = false;
                        }
                    } else if (successor.i >= 2) {
                        spillLeft = true;
                    } else {
                        spillLeft = predecessor.i < successor.i;
                    }
                } else if (predecessor.i < 2) {
                    spillLeft = true;
                } else {
                    spillLeft = false;
                    attachmentParent = currentNode;
                }

                DoubleTreeMap.Entry<V> displacedEntry;
                if (!spillLeft) {
                    displacedEntry = currentNode.k[1];
                    System.arraycopy(currentNode.k, comparison, currentNode.k, comparison + 1, 1 - comparison);
                    currentNode.k[comparison] = new DoubleTreeMap.Entry<>(key, value);
                } else {
                    displacedEntry = currentNode.k[0];
                    int newEntryIndex = comparison - 1;
                    System.arraycopy(currentNode.k, 1, currentNode.k, 0, newEntryIndex);
                    currentNode.k[newEntryIndex] = new DoubleTreeMap.Entry<>(key, value);
                }
                if (attachmentParent != null) {
                    DoubleTreeMap.Node<V> insertedNode = this.createNode(displacedEntry);
                    if (!attachAsLeftChild)
                        this.linkRightChild(attachmentParent, insertedNode);
                    else
                        this.linkLeftChild(attachmentParent, insertedNode);
                    this.fixAfterInsertion(insertedNode);
                } else if (spillLeft) {
                    this.appendEntry(predecessor, displacedEntry);
                } else {
                    this.prependEntry(successor, displacedEntry);
                }
            }
        } else if (parentNode == null)
            this.e = this.createNode(key, value);
        else if (parentNode.i >= 2) {
            DoubleTreeMap.Node<V> insertedNode = this.createNode(key, value);
            if (comparison >= 0)
                this.linkRightChild(parentNode, insertedNode);
            else
                this.linkLeftChild(parentNode, insertedNode);
            this.fixAfterInsertion(insertedNode);
        } else if (comparison < 0)
            this.prependEntry(parentNode, key, value);
        else
            this.appendEntry(parentNode, key, value);
        return null;
    }

    /// Removes the entry for `key` and returns its previous value.
    ///
    /// A `null` result means either that the key was absent or that the removed entry stored a
    /// `null` value.
    public V remove(double key) {
        if (this.a == 0)
            return null;
        DoubleTreeMap.Node<V> currentNode = this.e;
        while (currentNode != null) {
            DoubleTreeMap.Entry<V>[] entries = currentNode.k;
            int firstIndex = currentNode.firstEntryIndex;
            int comparison = this.compareKeys(key, entries[firstIndex].a);
            if (comparison < 0) {
                currentNode = currentNode.e;
                continue;
            }
            if (comparison == 0) {
                V removedValue = currentNode.k[firstIndex].b;
                this.removeFirstEntry(currentNode);
                return removedValue;
            }
            int lastIndex = currentNode.h;
            if (firstIndex != lastIndex)
                comparison = this.compareKeys(key, entries[lastIndex].a);
            if (comparison > 0) {
                currentNode = currentNode.f;
                continue;
            }
            if (comparison == 0) {
                V removedValue = currentNode.k[lastIndex].b;
                this.removeLastEntry(currentNode);
                return removedValue;
            }
            int lowIndex = firstIndex + 1;
            int highIndex = lastIndex - 1;
            while (lowIndex <= highIndex) {
                int middleIndex = lowIndex + highIndex >> 1;
                comparison = this.compareKeys(key, entries[middleIndex].a);
                if (comparison > 0)
                    lowIndex = middleIndex + 1;
                else if (comparison < 0)
                    highIndex = middleIndex - 1;
                else {
                    V removedValue = currentNode.k[middleIndex].b;
                    this.removeMiddleEntry(currentNode, middleIndex);
                    return removedValue;
                }
            }
            return null;
        }
        return null;
    }

    /// Returns the number of stored entries.
    public int size() {
        return this.a;
    }
}
