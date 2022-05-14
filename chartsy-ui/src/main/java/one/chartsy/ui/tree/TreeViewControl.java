/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.tree;

import one.chartsy.core.Refreshable;
import one.chartsy.ui.nodes.EntityNode;
import one.chartsy.ui.nodes.NodeActions;
import org.openide.ErrorManager;
import org.openide.nodes.*;

import java.beans.PropertyChangeEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TreeViewControl implements NodeListener {

    private final NavigableMap<Key, EntityNode<?>> cache = new TreeMap<>();
    private final EntityNode<?> root;

    public void addEntry(EntityNode<?> node) {
        Key key = new Key(node.getEntity().getClass(), node.getEntityIdentifier());
        synchronized (cache) {
            cache.put(key, node);
        }
    }

    public void removeEntry(EntityNode<?> node) {
        var fromKey = new Key(node.getEntity().getClass(), node.getEntityIdentifier(), Integer.MIN_VALUE);
        synchronized (cache) {
            var iter = cache.tailMap(fromKey).entrySet().iterator();
            while (iter.hasNext()) {
                var e = iter.next();
                if (!fromKey.matches(e.getKey()))
                    break;
                if (e.getValue() == node)
                    iter.remove();
            }
        }
    }

    public TreeViewControl(EntityNode<?> root) {
        this.root = root;
        this.root.addNodeListener(this);
        addEntry(root);
    }
    
    public static final class Key implements Comparable<Key> {
        private static final AtomicInteger SEQ = new AtomicInteger();
        private final String type;
        private final Comparable<Object> id;
        private final int seq;
        
        Key(Class<?> type, Comparable<?> id) {
            this(type, id, SEQ.getAndIncrement());
        }
        
        @SuppressWarnings("unchecked")
        Key(Class<?> type, Comparable<?> id, int seq) {
            this.type = type.getName();
            this.id = (Comparable<Object>) id;
            this.seq = seq;
        }
        
        public boolean matches(Key o) {
            return Objects.equals(type, o.type) && Objects.equals(id, o.id);
        }
        
        @Override
        public int compareTo(Key o) {
            int cmp = type.compareTo(o.type);
            if (cmp == 0) {
                if (id == null)
                    return (o.id == null)? 0: -1;
                if (o.id == null)
                    return 1;
                cmp = id.compareTo(o.id);
            }
            return cmp;
        }
        
        @Override
        public String toString() {
            return "Key[" + type + ", " + id + ", " + seq + "]";
        }
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // currently ignored
    }

    @Override
    public void childrenAdded(NodeMemberEvent evt) {
        // attach itself as listener on all non-leaf children
        for (Node node : evt.getDelta()) {
            if (!node.isLeaf())
                node.addNodeListener(this);

            if (node instanceof EntityNode)
                addEntry((EntityNode<?>) node);
        }
    }
    
    @Override
    public void childrenRemoved(NodeMemberEvent evt) {
        Node[] delta = evt.getDelta();
        Deque<Node> worklist = new ArrayDeque<>(delta.length);
        for (Node value : delta)
            worklist.addLast(value);
        
        while (!worklist.isEmpty()) {
            Node node = worklist.pollFirst();
            if (!node.isLeaf()) {
                node.removeNodeListener(this);
                Children children = node.getChildren();
                if (NodeActions.childrenInitialized(children))
                    for (int i = 0, count = children.getNodesCount(); i < count; i++)
                        worklist.addLast(children.getNodeAt(i));
            }
            if (node instanceof EntityNode)
                removeEntry((EntityNode<?>) node);
        }
    }
    
    @Override
    public void childrenReordered(NodeReorderEvent evt) {
        // currently ignored
    }
    
    @Override
    public void nodeDestroyed(NodeEvent evt) {
        // remove itself from the node
        evt.getNode().removeNodeListener(this);
    }
    
    /**
     * The dummy implementation of {@code Collection} interface intended to not collect anything.
     */
    private static final Collection<EntityNode<?>> notCollecting = new AbstractCollection<>() {
        
        @Override
        public Iterator<EntityNode<?>> iterator() {
            return Collections.emptyIterator();
        }
        
        @Override
        public boolean contains(Object o) {
            return false;
        }
        
        @Override
        public boolean add(EntityNode<?> e) {
            return true;
        }
        
        @Override
        public int size() {
            return 0;
        }
    };
    
    public boolean invalidateRoot() {
        return invalidateRoot(notCollecting);
    }
    
    public boolean invalidateRoot(Collection<EntityNode<?>> refreshedNodes) {
        return refresh(root, refreshedNodes);
    }
    
    public boolean invalidate(Class<?> type, Comparable<?> id) {
        return invalidate(new Key(type, id, Integer.MIN_VALUE), notCollecting);
    }
    
    public boolean invalidate(Class<?> type, Comparable<?> id, Collection<EntityNode<?>> refreshedNodes) {
        if (id == null)
            // special case for root invalidation
            return refresh(root, refreshedNodes);
        else
            // regular invalidation
            return invalidate(new Key(type, id, Integer.MIN_VALUE), refreshedNodes);
    }
    
    public boolean invalidate(Key fromKey, Collection<EntityNode<?>> refreshedNodes) {
        boolean found = false;
        synchronized (cache) {
            var iter = cache.tailMap(fromKey).entrySet().iterator();
            while (iter.hasNext()) {
                try {
                    Map.Entry<Key, EntityNode<?>> e = iter.next();
                    if (!fromKey.matches(e.getKey()))
                        break;
                    found |= refresh(e.getValue(), refreshedNodes);
                } catch (Exception e) {
                    ErrorManager.getDefault().notify(e);
                }
            }
        }
        return found;
    }
    
    protected boolean refresh(EntityNode<?> node, Collection<EntityNode<?>> refreshedNodes) {
        while (true) {
            Refreshable r = node.getRefreshable();
            if (r != null) {
                if (!refreshedNodes.contains(node)) {
                    refreshedNodes.add(node);
                    r.refresh();
                }
                return true;
            }

            Node parent = node.getParentNode();
            while (!(parent instanceof EntityNode)) {
                if (parent == null)
                    return false;
                else
                    parent = parent.getParentNode();
            }
            node = (EntityNode<?>) parent;
        }
    }
}
