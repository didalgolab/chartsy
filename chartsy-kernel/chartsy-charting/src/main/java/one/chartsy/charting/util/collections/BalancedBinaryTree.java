package one.chartsy.charting.util.collections;

/// Stores intrusive {@link Entry entries} in caller-defined order while supporting
/// absolute-position lookup and insertion relative to an existing node.
///
/// Each {@link Node} keeps up to five adjacent entries in one fixed-size bucket. The
/// buckets themselves are arranged both as a red-black tree, which translates between
/// node-local slots and absolute indexes, and as a predecessor/successor chain, which
/// supports fast splicing near a known anchor.
///
/// This type never compares entries or derives ordering from their payload. Callers are
/// responsible for choosing insertion anchors that preserve whatever global order their
/// higher-level data structure expects.
///
/// ### API Notes
///
/// - Entries are intrusive. The tree updates {@link Entry#getHoldingNode()} whenever an
///   entry moves between buckets.
/// - Mutations may redistribute entries between neighboring nodes or remove a node
///   entirely, so cached {@link Node} references should be treated as short-lived
///   anchors.
/// - Removing an entry detaches it from the tree structure, but the implementation does
///   not clear its cached holding-node reference.
public class BalancedBinaryTree {

    /// Base type for payload objects stored directly inside a {@link BalancedBinaryTree}.
    ///
    /// The tree owns the {@linkplain #getHoldingNode() holding-node cache} while the
    /// entry is linked. Subclasses typically add only the payload fields needed by the
    /// caller's higher-level ordering logic.
    ///
    /// ### API Note
    ///
    /// The cached holding node is an implementation aid rather than a lifetime
    /// guarantee. After an entry is removed, the reference may still point at the bucket
    /// that last held it.
    public static abstract class Entry {
        Node holdingNode;

        /// Returns the node that currently contains this entry.
        ///
        /// This is primarily an anchor for cooperating data structures that continue
        /// local searches from a previously known position. It is meaningful only while
        /// the caller knows the entry is still linked into a tree.
        ///
        /// @return the bucket currently holding this entry, or {@code null} before the
        ///         entry has been inserted into any tree
        public final Node getHoldingNode() {
            return holdingNode;
        }
    }

    /// Holds a consecutive run of up to five {@link Entry entries} inside a
    /// {@link BalancedBinaryTree}.
    ///
    /// Nodes are the tree's structural buckets. Each node exposes subtree size for
    /// absolute-index translation, node-local entry access for in-bucket searches, and
    /// left/right child links for traversing the red-black structure. Neighboring
    /// buckets are reached through {@link BalancedBinaryTree#getPredecessor(Node)} and
    /// {@link BalancedBinaryTree#getSuccessor(Node)}.
    ///
    /// ### API Note
    ///
    /// Callers should treat node objects as transient insertion anchors. Tree mutations
    /// may spill entries into a predecessor or successor bucket, split a full bucket, or
    /// delete a bucket entirely.
    public static class Node {
        static final int MAX_ENTRIES = 5;
        Node predecessor;
        Node successor;
        Node parent;
        Node leftBranch;
        Node rightBranch;
        Entry[] entries;
        int entryCount;
        int branchSize;
        int firstEntryIndex;
        int lastEntryIndex;
        boolean red;

        public Node() {
            firstEntryIndex = 0;
            lastEntryIndex = -1;
            entries = new Entry[MAX_ENTRIES];
        }

        /// Returns the total number of entries stored in this node and all of its
        /// descendants.
        ///
        /// This cached count is what lets the tree translate between absolute indexes
        /// and node-local positions without flattening the structure first.
        public final int getBranchSize() {
            return branchSize;
        }

        /// Returns how many occupied entry slots currently belong to this bucket.
        public final int getEntriesCount() {
            return entryCount;
        }

        /// Returns the entry at a node-local index.
        ///
        /// The index is relative to the node's occupied entries, not the raw backing
        /// array slot. In other words, `0` always refers to this bucket's first stored
        /// entry even when the implementation keeps slack on the left side of the array.
        ///
        /// @param index zero-based index within this node
        /// @return the entry stored at {@code index}
        /// @throws ArrayIndexOutOfBoundsException if {@code index} is outside
        ///         {@code [0, getEntriesCount())}
        public final Entry getEntry(int index) {
            if (index >= 0 && index < getEntriesCount())
                    return entries[index + firstEntryIndex];
            throw new ArrayIndexOutOfBoundsException(index);
        }

        /// Returns the left child in the red-black tree structure.
        public final Node getLeftBranch() {
            return leftBranch;
        }

        /// Returns the right child in the red-black tree structure.
        public final Node getRightBranch() {
            return rightBranch;
        }
    }

    private static int computeBranchSize(Node node) {
        int leftBranchSize = (node.leftBranch != null) ? node.leftBranch.branchSize : 0;
        int rightBranchSize = (node.rightBranch != null) ? node.rightBranch.branchSize : 0;
        return node.entryCount + leftBranchSize + rightBranchSize;
    }

    int size;

    Node root;

    public BalancedBinaryTree() {
        root = null;
        size = 0;
    }

    private Node createNode(Entry entry) {
        Node node = new Node();
        node.entries[0] = entry;
        node.firstEntryIndex = 0;
        node.lastEntryIndex = 0;
        if (entry == null) {
            node.entryCount = 0;
        } else {
            node.entryCount = 1;
            size++;
            entry.holdingNode = node;
        }
        return node;
    }

    private void clearNodeEntries(Entry[] entries, Node node) {
        for (int entryIndex = node.firstEntryIndex; entryIndex <= node.lastEntryIndex; entryIndex++) {
            entries[entryIndex] = null;
            node.entryCount--;
        }
    }

    private Node appendEntry(Node lastNode, Entry entry) {
        Node targetNode = lastNode;
        if (targetNode == null) {
            root = createNode(entry);
            targetNode = root;
        } else if (targetNode.entryCount < Node.MAX_ENTRIES) {
            appendToNode(targetNode, entry);
        } else {
            Node newNode = createNode(entry);
            linkNodeAfter(targetNode, newNode);
            rebalanceAfterInsertion(newNode);
            targetNode = newNode;
        }
        updateBranchSizesUpward(targetNode);
        return targetNode;
    }

    private int countEntriesBeforeChild(Node ancestor, Node child) {
        if (ancestor == null || child == null)
            return 0;

        int entryCountBeforeChild = countEntriesBeforeChild(ancestor.parent, ancestor);
        if (ancestor.rightBranch == child) {
            if (ancestor.leftBranch != null)
                entryCountBeforeChild += ancestor.leftBranch.branchSize;
            entryCountBeforeChild += ancestor.getEntriesCount();
        }
        return entryCountBeforeChild;
    }

    /// Removes an entry from the middle of a bucket, preferring to collapse the
    /// remaining entries into a neighboring bucket when one has enough slack.
    private void deleteEntryAt(Node node, int entryIndex) {
        Node predecessor = node.predecessor;
        if (predecessor != null && Node.MAX_ENTRIES - 1 - predecessor.lastEntryIndex > node.entryCount) {
            int firstEntryIndex = node.firstEntryIndex;
            int entriesBeforeRemoved = entryIndex - firstEntryIndex;
            int predecessorLastIndex = predecessor.lastEntryIndex;
            System.arraycopy(node.entries, firstEntryIndex, predecessor.entries, predecessor.lastEntryIndex + 1,
                    entriesBeforeRemoved);
            predecessor.lastEntryIndex += entriesBeforeRemoved;

            int entriesAfterRemoved = node.lastEntryIndex - entryIndex;
            System.arraycopy(node.entries, entryIndex + 1, predecessor.entries, predecessor.lastEntryIndex + 1,
                    entriesAfterRemoved);
            predecessor.lastEntryIndex += entriesAfterRemoved;
            predecessor.entryCount += node.entryCount - 1;

            for (int movedEntryIndex = predecessorLastIndex + 1; movedEntryIndex <= predecessor.lastEntryIndex;
                    movedEntryIndex++) {
                predecessor.entries[movedEntryIndex].holdingNode = predecessor;
            }

            deleteNode(node);
            updateBranchSizesUpward(predecessor);
            size--;
            return;
        }

        Node successor = node.successor;
        if (successor != null && successor.firstEntryIndex > node.entryCount) {
            int firstEntryIndex = node.firstEntryIndex;
            int originalSuccessorFirstIndex = successor.firstEntryIndex;
            int insertionStartIndex = successor.firstEntryIndex - node.entryCount + 1;
            successor.firstEntryIndex = insertionStartIndex;

            int entriesBeforeRemoved = entryIndex - firstEntryIndex;
            System.arraycopy(node.entries, firstEntryIndex, successor.entries, insertionStartIndex,
                    entriesBeforeRemoved);

            int targetIndex = insertionStartIndex + entriesBeforeRemoved;
            int entriesAfterRemoved = node.lastEntryIndex - entryIndex;
            System.arraycopy(node.entries, entryIndex + 1, successor.entries, targetIndex, entriesAfterRemoved);
            successor.entryCount += node.entryCount - 1;

            for (int movedEntryIndex = insertionStartIndex; movedEntryIndex < originalSuccessorFirstIndex;
                    movedEntryIndex++) {
                successor.entries[movedEntryIndex].holdingNode = successor;
            }

            deleteNode(node);
            updateBranchSizesUpward(successor);
            size--;
            return;
        }

        int entriesAfterRemoved = node.lastEntryIndex - entryIndex;
        int firstEntryIndex = node.firstEntryIndex;
        int entriesBeforeRemoved = entryIndex - firstEntryIndex;
        if (entriesAfterRemoved > entriesBeforeRemoved) {
            System.arraycopy(node.entries, firstEntryIndex, node.entries, firstEntryIndex + 1, entriesBeforeRemoved);
            updateBranchSizesUpward(node);

            Node previousNode = node.predecessor;
            if (previousNode != null && previousNode.entryCount == 1) {
                node.entries[firstEntryIndex] = previousNode.entries[previousNode.firstEntryIndex];
                node.entries[firstEntryIndex].holdingNode = node;
                deleteNode(previousNode);
                updateBranchSizesUpward(node);
                size--;
                return;
            }

            node.entries[firstEntryIndex] = null;
            node.firstEntryIndex++;
            node.entryCount--;
            updateBranchSizesUpward(node);
        } else {
            System.arraycopy(node.entries, entryIndex + 1, node.entries, entryIndex, entriesAfterRemoved);
            updateBranchSizesUpward(node);

            Node nextNode = node.successor;
            if (nextNode != null && nextNode.entryCount == 1) {
                node.entries[node.lastEntryIndex] = nextNode.entries[nextNode.firstEntryIndex];
                node.entries[node.lastEntryIndex].holdingNode = node;
                deleteNode(nextNode);
                updateBranchSizesUpward(node);
                size--;
                return;
            }

            node.entries[node.lastEntryIndex] = null;
            node.lastEntryIndex--;
            node.entryCount--;
            updateBranchSizesUpward(node);
        }

        size--;
    }

    /// Removes the first entry of a bucket, collapsing with neighbors when that keeps
    /// the bucket layout denser.
    private void deleteFirstEntry(Node node) {
        int firstEntryIndex = node.firstEntryIndex;
        if (node.entryCount == 1) {
            deleteNode(node);
            size--;
            return;
        }

        Node predecessor = node.predecessor;
        if (predecessor != null && Node.MAX_ENTRIES - 1 - predecessor.lastEntryIndex > node.entryCount) {
            int movedEntryCount = node.lastEntryIndex - firstEntryIndex;
            System.arraycopy(node.entries, firstEntryIndex + 1, predecessor.entries, predecessor.lastEntryIndex + 1,
                    movedEntryCount);
            predecessor.lastEntryIndex += movedEntryCount;
            predecessor.entryCount += movedEntryCount;
            for (int movedEntryIndex = firstEntryIndex + 1; movedEntryIndex < firstEntryIndex + 1 + movedEntryCount;
                    movedEntryIndex++) {
                node.entries[movedEntryIndex].holdingNode = predecessor;
            }
            deleteNode(node);
            updateBranchSizesUpward(predecessor);
            size--;
            return;
        }

        Node successor = node.successor;
        if (successor != null && successor.firstEntryIndex > node.entryCount) {
            int movedEntryCount = node.lastEntryIndex - firstEntryIndex;
            int insertionStartIndex = successor.firstEntryIndex - movedEntryCount;
            successor.firstEntryIndex = insertionStartIndex;
            System.arraycopy(node.entries, firstEntryIndex + 1, successor.entries, insertionStartIndex,
                    movedEntryCount);
            successor.entryCount += movedEntryCount;
            for (int movedEntryIndex = firstEntryIndex + 1; movedEntryIndex < firstEntryIndex + 1 + movedEntryCount;
                    movedEntryIndex++) {
                node.entries[movedEntryIndex].holdingNode = successor;
            }
            deleteNode(node);
            updateBranchSizesUpward(successor);
            size--;
            return;
        }

        node.entries[firstEntryIndex] = null;
        node.entryCount--;
        int entriesToShift = node.lastEntryIndex - node.firstEntryIndex;
        System.arraycopy(node.entries, node.firstEntryIndex + 1, node.entries, node.firstEntryIndex, entriesToShift);
        node.entries[node.lastEntryIndex] = null;
        node.lastEntryIndex--;

        Node predecessorWithSingleEntry = node.predecessor;
        updateBranchSizesUpward(node);
        if (predecessorWithSingleEntry != null && predecessorWithSingleEntry.entryCount == 1) {
            predecessorWithSingleEntry.entries[predecessorWithSingleEntry.firstEntryIndex].holdingNode = node;
            System.arraycopy(node.entries, node.firstEntryIndex, node.entries, node.firstEntryIndex + 1,
                    node.entryCount);
            node.entries[node.firstEntryIndex] = predecessorWithSingleEntry.entries[predecessorWithSingleEntry.firstEntryIndex];
            node.entryCount++;
            node.lastEntryIndex++;
            deleteNode(predecessorWithSingleEntry);
            updateBranchSizesUpward(node);
        }
        size--;
    }

    private void prependEntry(Node node, Entry entry) {
        if (node.firstEntryIndex != 0) {
            node.firstEntryIndex--;
        } else {
            System.arraycopy(node.entries, 0, node.entries, 1, node.entryCount);
            node.lastEntryIndex++;
        }
        node.entryCount++;
        size++;
        entry.holdingNode = node;
        node.entries[node.firstEntryIndex] = entry;
        updateBranchSizesUpward(node);
    }

    private void replaceInParent(Node node, Node replacement) {
        Node parent = node.parent;
        replacement.parent = parent;
        if (parent == null)
            root = replacement;
        else if (node != parent.leftBranch)
            parent.rightBranch = replacement;
        else
            parent.leftBranch = replacement;
    }

    /// Removes the last entry of a bucket, mirroring the left-edge removal strategy.
    private void deleteLastEntry(Node node) {
        int lastEntryIndex = node.lastEntryIndex;
        if (node.entryCount == 1) {
            deleteNode(node);
            size--;
            return;
        }

        Node predecessor = node.predecessor;
        if (predecessor != null && Node.MAX_ENTRIES - 1 - predecessor.lastEntryIndex > node.entryCount) {
            int firstEntryIndex = node.firstEntryIndex;
            int movedEntryCount = lastEntryIndex - firstEntryIndex;
            System.arraycopy(node.entries, firstEntryIndex, predecessor.entries, predecessor.lastEntryIndex + 1,
                    movedEntryCount);
            predecessor.lastEntryIndex += movedEntryCount;
            predecessor.entryCount += movedEntryCount;
            for (int movedEntryIndex = firstEntryIndex; movedEntryIndex < firstEntryIndex + movedEntryCount;
                    movedEntryIndex++) {
                node.entries[movedEntryIndex].holdingNode = predecessor;
            }
            deleteNode(node);
            updateBranchSizesUpward(predecessor);
            size--;
            return;
        }

        Node successor = node.successor;
        if (successor != null && successor.firstEntryIndex > node.entryCount) {
            int firstEntryIndex = node.firstEntryIndex;
            int movedEntryCount = lastEntryIndex - firstEntryIndex;
            int insertionStartIndex = successor.firstEntryIndex - movedEntryCount;
            successor.firstEntryIndex = insertionStartIndex;
            System.arraycopy(node.entries, firstEntryIndex, successor.entries, insertionStartIndex, movedEntryCount);
            successor.entryCount += movedEntryCount;
            for (int movedEntryIndex = firstEntryIndex; movedEntryIndex < firstEntryIndex + movedEntryCount;
                    movedEntryIndex++) {
                node.entries[movedEntryIndex].holdingNode = successor;
            }
            deleteNode(node);
            updateBranchSizesUpward(successor);
            size--;
            return;
        }

        node.entries[lastEntryIndex] = null;
        node.lastEntryIndex--;
        node.entryCount--;
        Node successorWithSingleEntry = node.successor;
        updateBranchSizesUpward(node);
        if (successorWithSingleEntry != null && successorWithSingleEntry.entryCount == 1) {
            node.entryCount++;
            node.lastEntryIndex++;
            successorWithSingleEntry.entries[successorWithSingleEntry.firstEntryIndex].holdingNode = node;
            node.entries[node.lastEntryIndex] = successorWithSingleEntry.entries[successorWithSingleEntry.firstEntryIndex];
            deleteNode(successorWithSingleEntry);
            updateBranchSizesUpward(node);
        }
        size--;
    }

    private void appendToNode(Node node, Entry entry) {
        if (node.lastEntryIndex != Node.MAX_ENTRIES - 1) {
            node.lastEntryIndex++;
        } else {
            int firstEntryIndex = node.firstEntryIndex;
            System.arraycopy(node.entries, firstEntryIndex, node.entries, firstEntryIndex - 1,
                    Node.MAX_ENTRIES - firstEntryIndex);
            node.firstEntryIndex--;
        }
        node.entryCount++;
        size++;
        entry.holdingNode = node;
        node.entries[node.lastEntryIndex] = entry;
        updateBranchSizesUpward(node);
    }

    private void replaceDeletedNode(Node node, Node replacement) {
        replaceInParent(node, replacement);
        if (!node.red)
            rebalanceAfterDeletion(replacement);
    }

    private void deleteNode(Node node) {
        if (node.rightBranch == null) {
            if (node.leftBranch == null)
                detachLeafNode(node);
            else
                replaceDeletedNode(node, node.leftBranch);
            unlinkNode(node);
            updateBranchSizesUpward(node.parent);
            return;
        }

        if (node.leftBranch == null) {
            replaceDeletedNode(node, node.rightBranch);
            unlinkNode(node);
            updateBranchSizesUpward(node.parent);
            return;
        }

        Node successor = node.successor;
        unlinkNode(node);
        if (successor.rightBranch == null) {
            detachLeafNode(successor);
            updateBranchSizesUpward(successor.parent);
        } else {
            replaceDeletedNode(successor, successor.rightBranch);
            updateBranchSizesUpward(successor.parent);
        }
        successor.leftBranch = node.leftBranch;
        if (node.leftBranch != null)
            node.leftBranch.parent = successor;
        successor.rightBranch = node.rightBranch;
        if (node.rightBranch != null)
            node.rightBranch.parent = successor;
        replaceInParent(node, successor);
        successor.red = node.red;
        updateBranchSizesUpward(successor);
    }

    private void linkNodeBefore(Node anchor, Node newNode) {
        if (anchor.leftBranch == null) {
            if (anchor.predecessor != null) {
                anchor.predecessor.successor = newNode;
                newNode.predecessor = anchor.predecessor;
            }
            newNode.parent = anchor;
            newNode.successor = anchor;
            anchor.leftBranch = newNode;
            anchor.predecessor = newNode;
            return;
        }

        Node insertionParent = anchor.leftBranch;
        while (insertionParent.rightBranch != null)
            insertionParent = insertionParent.rightBranch;

        newNode.parent = insertionParent;
        newNode.predecessor = insertionParent;
        newNode.successor = anchor;
        anchor.predecessor = newNode;
        insertionParent.rightBranch = newNode;
        insertionParent.successor = newNode;
    }

    /// Removes every entry from the tree.
    ///
    /// ### API Note
    ///
    /// Entries are detached from the tree structure, but callers should not rely on
    /// their cached holding-node reference being cleared.
    public void deleteAll() {
        clearSubtree(root);
        root = null;
        size = 0;
    }

    /// Removes an entry that is currently linked into this tree.
    ///
    /// The tree may merge buckets or move neighboring entries between nodes to preserve
    /// the fixed-capacity node layout. Cached node references held elsewhere may become
    /// stale after this call even if the removed entry was not stored in that node.
    ///
    /// @param entry entry currently stored in this tree
    public void deleteEntry(Entry entry) {
        Node node = entry.getHoldingNode();
        Entry[] entries = node.entries;
        int entryIndex = node.firstEntryIndex;
        while (entryIndex <= node.lastEntryIndex && entries[entryIndex] != entry)
            entryIndex++;

        if (entryIndex == node.firstEntryIndex)
            deleteFirstEntry(node);
        else if (entryIndex == node.lastEntryIndex)
            deleteLastEntry(node);
        else
            deleteEntryAt(node, entryIndex);
    }

    private void detachLeafNode(Node node) {
        Node parent = node.parent;
        if (parent == null) {
            root = null;
        } else {
            if (node != parent.leftBranch)
                parent.rightBranch = null;
            else
                parent.leftBranch = null;
            if (!node.red)
                rebalanceAfterDeletion(parent);
        }
    }

    private void linkNodeAfter(Node anchor, Node newNode) {
        Node rightBranch = anchor.rightBranch;
        Node successor = anchor.successor;
        if (rightBranch == null) {
            anchor.successor = newNode;
            anchor.rightBranch = newNode;
            newNode.parent = anchor;
            newNode.predecessor = anchor;
            newNode.successor = successor;
            if (successor != null)
                successor.predecessor = newNode;
            return;
        }

        if (successor != null && rightBranch == successor) {
            anchor.successor = newNode;
            newNode.parent = anchor;
            anchor.rightBranch = newNode;
            newNode.predecessor = anchor;
            newNode.successor = successor;
            successor.predecessor = newNode;
            successor.parent = newNode;
            newNode.rightBranch = rightBranch;
            return;
        }

        if (successor != null) {
            Node insertionAnchor = anchor.rightBranch;
            while (insertionAnchor.leftBranch != null)
                insertionAnchor = insertionAnchor.leftBranch;
            linkNodeBefore(insertionAnchor, newNode);
        }
    }

    private void unlinkNode(Node node) {
        if (node.predecessor != null)
            node.predecessor.successor = node.successor;
        if (node.successor != null)
            node.successor.predecessor = node.predecessor;
    }

    private void rebalanceAfterDeletion(Node node) {
        Node current = node;
        while (current != root && !current.red) {
            Node sibling;
            if (current == current.parent.leftBranch) {
                sibling = current.parent.rightBranch;
                if (sibling == null) {
                    current = current.parent;
                    continue;
                }

                if (sibling.red) {
                    sibling.red = false;
                    current.parent.red = true;
                    rotateLeft(current.parent);
                    updateBranchSize(current.parent);
                    sibling = current.parent.rightBranch;
                    if (sibling == null) {
                        current = current.parent;
                        continue;
                    }
                }

                boolean siblingChildrenBlack = (sibling.leftBranch == null || !sibling.leftBranch.red)
                        && (sibling.rightBranch == null || !sibling.rightBranch.red);
                if (siblingChildrenBlack) {
                    sibling.red = true;
                    current = current.parent;
                    continue;
                }

                if (sibling.rightBranch == null || !sibling.rightBranch.red) {
                    sibling.leftBranch.red = false;
                    sibling.red = true;
                    rotateRight(sibling);
                    updateBranchSize(sibling);
                    sibling = current.parent.rightBranch;
                }

                sibling.red = current.parent.red;
                current.parent.red = false;
                sibling.rightBranch.red = false;
                rotateLeft(current.parent);
                updateBranchSize(current.parent);
                current = root;
            } else {
                sibling = current.parent.leftBranch;
                if (sibling == null) {
                    current = current.parent;
                    continue;
                }

                if (sibling.red) {
                    sibling.red = false;
                    current.parent.red = true;
                    rotateRight(current.parent);
                    updateBranchSize(current.parent);
                    sibling = current.parent.leftBranch;
                    if (sibling == null) {
                        current = current.parent;
                        continue;
                    }
                }

                boolean siblingChildrenBlack = (sibling.leftBranch == null || !sibling.leftBranch.red)
                        && (sibling.rightBranch == null || !sibling.rightBranch.red);
                if (siblingChildrenBlack) {
                    sibling.red = true;
                    current = current.parent;
                    continue;
                }

                if (sibling.leftBranch == null || !sibling.leftBranch.red) {
                    sibling.rightBranch.red = false;
                    sibling.red = true;
                    rotateLeft(sibling);
                    updateBranchSize(sibling);
                    sibling = current.parent.leftBranch;
                }

                sibling.red = current.parent.red;
                current.parent.red = false;
                sibling.leftBranch.red = false;
                rotateRight(current.parent);
                updateBranchSize(current.parent);
                current = root;
            }
        }
        current.red = false;
    }

    /// Returns the entry at a zero-based position in the full in-order traversal.
    ///
    /// This lookup walks the bucket tree instead of flattening it. Callers that already
    /// hold a nearby {@link Node} may still prefer local navigation over repeated random
    /// indexed access.
    ///
    /// @param index absolute zero-based index across the entire tree
    /// @return the entry at {@code index}, or {@code null} if {@code index} is not
    ///         smaller than {@link #getSize()}
    public final Entry getEntryAt(int index) {
        if (index >= getSize())
            return null;

        Node node = root;
        int entriesBeforeNode = 0;
        int leftBranchSize;
        while (true) {
            leftBranchSize = (node.leftBranch == null) ? 0 : node.leftBranch.branchSize;
            int nodeEntryCount = node.entryCount;
            if (leftBranchSize + entriesBeforeNode <= index
                    && index < nodeEntryCount + leftBranchSize + entriesBeforeNode) {
                break;
            }
            if (index < leftBranchSize + entriesBeforeNode) {
                node = node.leftBranch;
            } else {
                entriesBeforeNode += leftBranchSize + nodeEntryCount;
                node = node.rightBranch;
            }
        }
        int localIndex = index - leftBranchSize - entriesBeforeNode;
        return node.entries[localIndex + node.firstEntryIndex];
    }

    /// Returns the zero-based in-order position of a linked entry.
    ///
    /// The result is meaningful only for entries that are still stored in this tree. The
    /// implementation trusts the entry's cached holding node and does not perform a full
    /// membership scan across the entire structure.
    ///
    /// @param entry entry currently linked into this tree
    /// @return absolute index of {@code entry}, or {@code -1} if it does not currently
    ///         report a holding node
    public final int getIndexOfEntry(Entry entry) {
        Node node = entry.getHoldingNode();
        if (node == null)
            return -1;

        int entryIndex = node.firstEntryIndex;
        while (entryIndex <= node.lastEntryIndex && node.entries[entryIndex] != entry)
            entryIndex++;

        int localIndex = entryIndex - node.firstEntryIndex;
        int indexBeforeNode = (node.leftBranch != null) ? node.leftBranch.branchSize : 0;
        indexBeforeNode += countEntriesBeforeChild(node.parent, node);
        return indexBeforeNode + localIndex;
    }

    /// Returns the previous node in the in-order node chain.
    ///
    /// This is the neighboring bucket used for adjacent insertions, not the parent in the
    /// branching structure.
    ///
    /// @param node node already linked into this tree
    /// @return the previous node, or {@code null} when {@code node} is the first bucket
    public final Node getPredecessor(Node node) {
        if (node == null)
            return null;
        return node.predecessor;
    }

    /// Returns the current root bucket of the branching structure used for indexed
    /// lookup.
    ///
    /// Rotations may replace the root during insertions and deletions even when the
    /// caller-visible entry order stays the same, so the value should be treated as a
    /// fresh traversal starting point rather than a stable identity.
    public final Node getRoot() {
        return root;
    }

    /// Returns the number of entries currently stored in the tree.
    ///
    /// This counts payload entries, not the number of buckets.
    public final int getSize() {
        return size;
    }

    /// Returns the next node in the in-order node chain.
    ///
    /// This is the neighboring bucket used for adjacent insertions, not the parent in the
    /// branching structure.
    ///
    /// @param node node already linked into this tree
    /// @return the next node, or {@code null} when {@code node} is the last bucket
    public final Node getSuccessor(Node node) {
        if (node == null)
            return null;
        return node.successor;
    }

    private void clearSubtree(Node node) {
        if (node != null) {
            clearSubtree(node.leftBranch);
            clearNodeEntries(node.entries, node);
            clearSubtree(node.rightBranch);
        }
    }

    private void rebalanceAfterInsertion(Node node) {
        Node current = node;
        current.red = true;
        while (current != root && current.parent.red) {
            Node uncle;
            if (current.parent == current.parent.parent.leftBranch) {
                uncle = current.parent.parent.rightBranch;
                if (uncle != null && uncle.red) {
                    current.parent.red = false;
                    uncle.red = false;
                    current.parent.parent.red = true;
                    current = current.parent.parent;
                    continue;
                }
                if (current == current.parent.rightBranch) {
                    current = current.parent;
                    rotateLeft(current);
                }
                current.parent.red = false;
                current.parent.parent.red = true;
                rotateRight(current.parent.parent);
            } else {
                uncle = current.parent.parent.leftBranch;
                if (uncle != null && uncle.red) {
                    current.parent.red = false;
                    uncle.red = false;
                    current.parent.parent.red = true;
                    current = current.parent.parent;
                    continue;
                }
                if (current == current.parent.leftBranch) {
                    current = current.parent;
                    rotateRight(current);
                }
                current.parent.red = false;
                current.parent.parent.red = true;
                rotateLeft(current.parent.parent);
            }
        }
        root.red = false;
    }

    /// Replaces the tree contents with the supplied entries in array order.
    ///
    /// This is a bulk-loading shortcut for callers that already hold entries in their
    /// desired traversal order. No comparison or sorting is performed.
    ///
    /// @param entries entries to append from left to right
    public void init(Entry[] entries) {
        deleteAll();
        Node lastNode = null;
        for (Entry entry : entries)
            lastNode = appendEntry(lastNode, entry);
    }

    /// Inserts an entry immediately after the last entry currently stored in a node.
    ///
    /// The anchor node must already belong to this tree. If that bucket is full, the
    /// tree may spill entries into a neighboring bucket or allocate a new bucket to
    /// preserve the per-node capacity limit.
    ///
    /// @param node  anchor node whose local tail defines the insertion point
    /// @param entry entry to insert after {@code node}'s current contents
    public void insertEntryAfter(Node node, Entry entry) {
        Node predecessor = node.predecessor;
        Node successor = node.successor;
        if (node.entryCount < Node.MAX_ENTRIES) {
            appendToNode(node, entry);
            return;
        }

        if (predecessor != null && predecessor.entryCount < Node.MAX_ENTRIES) {
            Entry shiftedEntry = node.entries[node.firstEntryIndex];
            System.arraycopy(node.entries, node.firstEntryIndex + 1, node.entries, node.firstEntryIndex,
                    node.entryCount - 1);
            node.entries[node.lastEntryIndex] = entry;
            entry.holdingNode = node;
            appendToNode(predecessor, shiftedEntry);
            updateBranchSizesUpward(node);
            return;
        }

        if (successor != null && successor.entryCount < Node.MAX_ENTRIES) {
            prependEntry(successor, entry);
            return;
        }

        if (predecessor != null && successor != null && predecessor.entryCount == Node.MAX_ENTRIES
                && successor.entryCount == Node.MAX_ENTRIES) {
            Node newNode = createNode(entry);
            linkNodeAfter(node, newNode);
            rebalanceAfterInsertion(newNode);
            updateBranchSizesUpward(newNode);
            updateBranchSizesUpward(node);
            return;
        }

        if (predecessor == null) {
            Entry shiftedEntry = node.entries[node.firstEntryIndex];
            System.arraycopy(node.entries, node.firstEntryIndex + 1, node.entries, node.firstEntryIndex,
                    node.entryCount - 1);
            node.entries[node.lastEntryIndex] = entry;
            entry.holdingNode = node;
            Node newNode = createNode(shiftedEntry);
            linkNodeBefore(node, newNode);
            rebalanceAfterInsertion(newNode);
            updateBranchSizesUpward(newNode);
            updateBranchSizesUpward(node);
            return;
        }

        if (successor == null) {
            Node newNode = createNode(entry);
            linkNodeAfter(node, newNode);
            rebalanceAfterInsertion(newNode);
            updateBranchSizesUpward(newNode);
            updateBranchSizesUpward(node);
        }
    }

    /// Inserts the first entry into the root bucket.
    ///
    /// ### API Note
    ///
    /// This is the empty-tree bootstrap operation. Callers should use it only when the
    /// tree does not already contain entries.
    ///
    /// @param entry first entry to store in the tree
    public void insertEntryAtRoot(Entry entry) {
        if (root == null)
            root = new Node();
        root.entries[0] = entry;
        root.entryCount = 1;
        size++;
        entry.holdingNode = root;
        updateBranchSizesUpward(root);
        root.firstEntryIndex = 0;
        root.lastEntryIndex = 0;
    }

    /// Inserts an entry immediately before the first entry currently stored in a node.
    ///
    /// The anchor node must already belong to this tree. If that bucket is full, the
    /// tree may spill entries into a neighboring bucket or allocate a new bucket to
    /// preserve the per-node capacity limit.
    ///
    /// @param node  anchor node whose local head defines the insertion point
    /// @param entry entry to insert before {@code node}'s current contents
    public void insertEntryBefore(Node node, Entry entry) {
        Node predecessor = node.predecessor;
        Node successor = node.successor;
        if (node.entryCount < Node.MAX_ENTRIES) {
            prependEntry(node, entry);
            return;
        }

        if (predecessor != null && predecessor.entryCount < Node.MAX_ENTRIES) {
            appendToNode(predecessor, entry);
            return;
        }

        if (successor != null && successor.entryCount < Node.MAX_ENTRIES) {
            Entry shiftedEntry = node.entries[node.lastEntryIndex];
            System.arraycopy(node.entries, node.firstEntryIndex, node.entries, node.firstEntryIndex + 1,
                    node.entryCount - 1);
            node.entries[node.firstEntryIndex] = entry;
            entry.holdingNode = node;
            prependEntry(successor, shiftedEntry);
            updateBranchSizesUpward(node);
            return;
        }

        if (predecessor != null && successor != null && predecessor.entryCount == Node.MAX_ENTRIES
                && successor.entryCount == Node.MAX_ENTRIES) {
            Node newNode = createNode(entry);
            linkNodeBefore(node, newNode);
            rebalanceAfterInsertion(newNode);
            updateBranchSizesUpward(newNode);
            updateBranchSizesUpward(node);
            return;
        }

        if (predecessor == null) {
            Node newNode = createNode(entry);
            linkNodeBefore(node, newNode);
            rebalanceAfterInsertion(newNode);
            updateBranchSizesUpward(newNode);
            updateBranchSizesUpward(node);
            return;
        }

        if (successor == null) {
            Entry shiftedEntry = node.entries[node.lastEntryIndex];
            System.arraycopy(node.entries, node.firstEntryIndex, node.entries, node.firstEntryIndex + 1,
                    node.entryCount - 1);
            node.entries[node.firstEntryIndex] = entry;
            entry.holdingNode = node;
            Node newNode = createNode(shiftedEntry);
            linkNodeAfter(node, newNode);
            rebalanceAfterInsertion(newNode);
            updateBranchSizesUpward(newNode);
            updateBranchSizesUpward(node);
        }
    }

    /// Inserts an entry at a node-local position.
    ///
    /// When the target bucket is full, the current last entry is spilled into a
    /// following bucket after the local insertion succeeds.
    ///
    /// @param node  node that already belongs to this tree
    /// @param index zero-based insertion position inside {@code node}
    /// @param entry entry to insert at {@code index}
    public void insertEntryInto(Node node, int index, Entry entry) {
        int firstEntryIndex = node.firstEntryIndex;
        int lastEntryIndex = node.lastEntryIndex;
        Entry[] reorderedEntries = new Entry[Node.MAX_ENTRIES];
        if (node.entryCount >= Node.MAX_ENTRIES) {
            Entry spilledEntry = node.entries[lastEntryIndex];
            System.arraycopy(node.entries, firstEntryIndex, reorderedEntries, 0, index);
            reorderedEntries[index] = entry;
            System.arraycopy(node.entries, index, reorderedEntries, index + 1, lastEntryIndex - index);
            System.arraycopy(reorderedEntries, 0, node.entries, 0, node.entryCount);
            entry.holdingNode = node;
            updateBranchSizesUpward(node);
            insertEntryAfter(node, spilledEntry);
            return;
        }

        if (index == 0 && firstEntryIndex == 0) {
            System.arraycopy(node.entries, 0, reorderedEntries, 1, node.entries.length - 1);
            reorderedEntries[0] = entry;
            System.arraycopy(reorderedEntries, 0, node.entries, 0, node.entries.length);
            node.lastEntryIndex++;
        } else if (firstEntryIndex <= 0) {
            reorderedEntries[index] = entry;
            System.arraycopy(node.entries, 0, reorderedEntries, 0, index);
            System.arraycopy(node.entries, index, reorderedEntries, index + 1, node.entries.length - index - 1);
            System.arraycopy(reorderedEntries, 0, node.entries, 0, node.entries.length);
            node.lastEntryIndex++;
        } else {
            int targetIndex = firstEntryIndex + index - 1;
            System.arraycopy(node.entries, 1, reorderedEntries, 0, targetIndex);
            reorderedEntries[targetIndex] = entry;
            System.arraycopy(node.entries, targetIndex + 1, reorderedEntries, targetIndex + 1,
                    node.entries.length - targetIndex - 1);
            System.arraycopy(reorderedEntries, 0, node.entries, 0, node.entries.length);
            node.firstEntryIndex--;
        }

        node.entryCount++;
        entry.holdingNode = node;
        size++;
        updateBranchSizesUpward(node);
    }

    private void rotateRight(Node node) {
        Node pivot = node.leftBranch;
        node.leftBranch = pivot.rightBranch;
        if (pivot.rightBranch != null)
            pivot.rightBranch.parent = node;
        pivot.parent = node.parent;
        if (node.parent == null)
            root = pivot;
        else if (node != node.parent.rightBranch)
            node.parent.leftBranch = pivot;
        else
            node.parent.rightBranch = pivot;
        pivot.rightBranch = node;
        node.parent = pivot;
    }

    private void rotateLeft(Node node) {
        Node pivot = node.rightBranch;
        node.rightBranch = pivot.leftBranch;
        if (pivot.leftBranch != null)
            pivot.leftBranch.parent = node;
        pivot.parent = node.parent;
        if (node.parent == null)
            root = pivot;
        else if (node != node.parent.leftBranch)
            node.parent.rightBranch = pivot;
        else
            node.parent.leftBranch = pivot;
        pivot.leftBranch = node;
        node.parent = pivot;
    }

    /// Recomputes cached subtree sizes from a node up to the root.
    ///
    /// Red child buckets may have stale cached sizes immediately after rebalancing
    /// rotations, so they are refreshed before the current bucket is recomputed.
    private void updateBranchSizesUpward(Node node) {
        Node current = node;
        while (current != null) {
            if (current.leftBranch != null && current.leftBranch.red)
                current.leftBranch.branchSize = computeBranchSize(current.leftBranch);
            if (current.rightBranch != null && current.rightBranch.red)
                current.rightBranch.branchSize = computeBranchSize(current.rightBranch);
            current.branchSize = computeBranchSize(current);
            current = current.parent;
        }
    }

    private void updateBranchSize(Node node) {
        if (node.leftBranch != null && node.leftBranch.red)
            node.leftBranch.branchSize = computeBranchSize(node.leftBranch);
        if (node.rightBranch != null && node.rightBranch.red)
            node.rightBranch.branchSize = computeBranchSize(node.rightBranch);
        node.branchSize = computeBranchSize(node);
    }
}
