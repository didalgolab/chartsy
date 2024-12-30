/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.swing;

import org.openide.nodes.Children;
import org.openide.nodes.Node;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class NodeSupport {

    public static boolean isInitialized(Children children) {
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

    public static boolean isVisualizerNode(Object node) {
        return node.getClass().getName().equals("org.openide.explorer.view.VisualizerNode");
    }

    public static boolean isWaitNode(Node node) {
        return node.getClass().getName().equals("org.openide.nodes.ChildFactory$WaitFilterNode");
    }
}
