/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.nodes;

import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.nodes.NodeAdapter;
import org.openide.nodes.NodeListener;
import org.openide.nodes.NodeMemberEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class NodeSelection extends NodeAdapter {

    public static void selectAllAdded(Node[] parents) {
        NodeListener listener = new NodeAdapter() {
            private final List<Node> allSelected = new ArrayList<>();

            @Override
            public void childrenAdded(NodeMemberEvent ev) {
                ev.getNode().removeNodeListener(this);
                Node[] added = ev.getDelta();
                if (added.length > 0) {
                    allSelected.addAll(Arrays.asList(added));
                    findExplorer(ev.getNode()).ifPresent(explorer ->
                            explorer.setExploredContext(ev.getNode(), allSelected.toArray(Node[]::new)));
                }
            }
        };
        Arrays.asList(parents).forEach(parent -> parent.addNodeListener(listener));
    }

    public static Optional<ExplorerManager> findExplorer(Node node) {
        Node parent;
        while ((parent = node.getParentNode()) != null)
            node = parent;

        return Optional.of(node.getLookup().lookup(ExplorerManager.class));
    }
}
