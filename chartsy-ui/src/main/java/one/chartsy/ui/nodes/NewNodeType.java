/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.nodes;

import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.actions.SystemAction;
import org.openide.util.datatransfer.NewType;

public class NewNodeType extends NewType {

    private final Node ownerNode;
    private final String resourceName;
    private final Class<? extends BaseNodeAction> creatorAction;

    public NewNodeType(Node ownerNode, String resourceName, Class<? extends BaseNodeAction> creatorAction) {
        this.ownerNode = ownerNode;
        this.resourceName = resourceName;
        this.creatorAction = creatorAction;
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(ownerNode.getClass(), resourceName);
    }

    @Override
    public void create() {
        SystemAction.get(creatorAction).performAction(ownerNode);
    }
}
