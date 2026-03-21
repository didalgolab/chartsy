package one.chartsy.charting.util.collections;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

/// Traverses a rooted hierarchy in pre-order.
///
/// The current element is yielded before any elements returned by [#getChildren(E)], and sibling
/// ordering is defined entirely by the child iterator that subclasses provide. `Chart` uses this
/// skeleton to walk nested renderer trees while still letting each renderer expose its own child
/// ordering.
///
/// The protected no-argument constructor is for subclasses that decide the root later. Those
/// subclasses must call `init(E)` or `initTraversalStack()` before exposing the iterator, because
/// this base class does not treat an uninitialized traversal as an empty one.
public abstract class PreorderIterator<E> implements Iterator<E> {
    private Deque<Iterator<E>> traversalStack;

    /// Creates an iterator whose traversal state will be supplied later by a subclass.
    protected PreorderIterator() {
    }

    /// Creates an iterator that starts at `root`.
    ///
    /// Passing `null` creates an exhausted iterator.
    public PreorderIterator(E root) {
        init(root);
    }

    /// Returns the direct children of `element` in the order they should be visited.
    ///
    /// The returned iterator is consumed immediately after `element` is yielded. Return an empty
    /// iterator for leaf elements; `null` is not supported.
    ///
    /// @param element traversal element whose children should be visited next
    /// @return iterator over the direct children of `element`
    protected abstract Iterator<E> getChildren(E element);

    /// Returns whether the traversal still has another element to visit.
    @Override
    public boolean hasNext() {
        return !traversalStack.isEmpty();
    }

    /// Reinitializes this iterator to start at `root`.
    ///
    /// Any traversal state accumulated so far is discarded.
    ///
    /// @param root root element to visit first, or `null` to reset the iterator to the exhausted
    ///             state
    protected void init(E root) {
        initTraversalStack();
        if (root != null)
            traversalStack.push(Collections.singletonList(root).iterator());
    }

    /// Replaces the current traversal state with a fresh empty stack.
    protected void initTraversalStack() {
        traversalStack = new ArrayDeque<>();
    }

    /// Returns the next element in root-first order.
    ///
    /// After yielding an element, this iterator pushes that element's child iterator before
    /// continuing with remaining siblings, so descendants are always visited immediately after
    /// their parent.
    ///
    /// @throws NoSuchElementException if the traversal is exhausted
    @Override
    public E next() {
        Iterator<E> siblings = traversalStack.element();
        E element = siblings.next();
        if (!siblings.hasNext())
            traversalStack.pop();

        Iterator<E> children = getChildren(element);
        if (children.hasNext())
            traversalStack.push(children);
        return element;
    }

    /// Always throws `UnsupportedOperationException`.
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
