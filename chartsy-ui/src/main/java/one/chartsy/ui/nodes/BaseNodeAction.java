/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.nodes;

import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.NodeAction;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Optional;

public abstract class BaseNodeAction extends NodeAction {
        
    public final void performAction(Node node) {
        performAction(new Node[] { node });
    }

    @Override
    protected void performAction(Node[] nodes) {
        performAction(List.of(nodes));
    }

    protected void performAction(List<Node> nodes) { };

    @Override
    public String getName() {
        Class<?> clazz = getClass();
        return NbBundle.getMessage(clazz, "action.".concat(clazz.getSimpleName()));
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }


    public static Node getRoot(Node node) {
        Node parent;
        while ((parent = node.getParentNode()) != null)
            node = parent;

        return node;
    }

    public static Optional<ApplicationContext> getApplicationContext(Node node) {
        return Optional.ofNullable(getRoot(node).getLookup().lookup(ApplicationContext.class));
    }
}
