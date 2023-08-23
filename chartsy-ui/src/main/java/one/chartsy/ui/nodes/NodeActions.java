/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.nodes;

import org.openide.explorer.view.TreeView;
import org.openide.nodes.Children;
import org.openide.nodes.Node;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;

public abstract class NodeActions {

    public static TreeView getTreeView(Node node) {
        return findService(node, TreeView.class);
    }

    public static TreeView getTreeView(Iterable<Node> nodes) {
        return getTreeView(nodes.iterator().next());
    }

    public static <T> T findService(Node node, Class<T> serviceType) {
        T service;
        do {
            service = node.getLookup().lookup(serviceType);
        } while (service == null && (node = node.getParentNode()) != null);

        return service;
    }

    public static boolean childrenInitialized(Children children) {
        try {
            return (boolean) childrenInitializedCheck.invokeExact(children);
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    private static final MethodHandle childrenInitializedCheck;
    static {
        try {
            Method m = Children.class.getDeclaredMethod("isInitialized");
            m.setAccessible(true);
            childrenInitializedCheck = MethodHandles.lookup().unreflect(m);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException e) {
            e.printStackTrace();
            throw new InternalError(e);
        }
    }

    public static class ExpandAll extends BaseNodeAction {
        @Override
        protected void performAction(List<Node> nodes) {
            if (nodes.isEmpty())
                return;

            TreeView tree = getTreeView(nodes);

            LinkedList<Node> worklist = new LinkedList<>(nodes);
            while (!worklist.isEmpty()) {
                Node parent = worklist.removeFirst();
                tree.expandNode(parent);

                Children children = parent.getChildren();
                int nodeCount = children.getNodesCount();
                for (int i = 0; i < nodeCount; i++) {
                    Node child = children.getNodeAt(i);
                    if (!child.isLeaf())
                        worklist.add(child);
                }
            }
        }

        @Override
        protected boolean enable(Node[] nodes) {
            for (Node node : nodes)
                if (node.isLeaf())
                    return false;
            return true;
        }
    }

    public static class CollapseAll extends BaseNodeAction {
        @Override
        protected void performAction(List<Node> nodes) {
            TreeView tree = getTreeView(nodes);

            for (Node parent : nodes) {
                Children children = parent.getChildren();
                int nodeCount = children.getNodesCount();
                for (int i = 0; i < nodeCount; i++)
                    tree.collapseNode(children.getNodeAt(i));

                tree.collapseNode(parent);
            }
        }

        @Override
        protected boolean enable(Node[] nodes) {
            return nodes.length > 0;
        }
    }
}
