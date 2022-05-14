/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.nodes;

import one.chartsy.core.Refreshable;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.openide.nodes.NodeListener;

public interface EntityNode<T> {

    T getEntity();

    Comparable<?> getEntityIdentifier();

    ChildFactory<?> getChildFactory();

    Node getParentNode();

    void addNodeListener(NodeListener l);

    default Refreshable getRefreshable() {
        ChildFactory<?> childFactory = getChildFactory();
        return (childFactory instanceof Refreshable)? (Refreshable) childFactory : null;
    }
}
