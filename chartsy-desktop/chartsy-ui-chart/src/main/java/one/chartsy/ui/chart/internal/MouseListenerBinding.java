/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Binds one mouse listener to a container and its current and future direct children.
 * Removed children are detached automatically; descendants of child containers are
 * outside the binding. Create and close bindings on the Swing event dispatch thread.
 */
public final class MouseListenerBinding extends ContainerAdapter implements AutoCloseable {
    private final Container container;
    private final Consumer<Component> attach;
    private final Consumer<Component> detach;
    private boolean closed;

    // Separate factories avoid registering unused MouseAdapter interfaces, which
    // can intercept events such as wheel scrolling intended for an ancestor.
    public static MouseListenerBinding bindMouseListener(Container container, MouseListener listener) {
        Objects.requireNonNull(listener, "listener");
        return new MouseListenerBinding(container,
                component -> component.addMouseListener(listener),
                component -> component.removeMouseListener(listener));
    }

    public static MouseListenerBinding bindMouseMotionListener(Container container, MouseMotionListener listener) {
        Objects.requireNonNull(listener, "listener");
        return new MouseListenerBinding(container,
                component -> component.addMouseMotionListener(listener),
                component -> component.removeMouseMotionListener(listener));
    }

    public static MouseListenerBinding bindMouseWheelListener(Container container, MouseWheelListener listener) {
        Objects.requireNonNull(listener, "listener");
        return new MouseListenerBinding(container,
                component -> component.addMouseWheelListener(listener),
                component -> component.removeMouseWheelListener(listener));
    }

    private MouseListenerBinding(Container container, Consumer<Component> attach, Consumer<Component> detach) {
        this.container = container;
        this.attach = attach;
        this.detach = detach;

        attach.accept(container);
        for (Component child : container.getComponents())
            attach.accept(child);

        container.addContainerListener(this);
    }

    @Override
    public void componentAdded(ContainerEvent event) {
        if (!closed)
            attach.accept(event.getChild());
    }

    @Override
    public void componentRemoved(ContainerEvent event) {
        detach.accept(event.getChild());
    }

    /**
     * Detaches this binding, leaving independently registered listeners intact.
     */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            container.removeContainerListener(this);
            detach.accept(container);
            for (Component child : container.getComponents())
                detach.accept(child);
        }
    }
}
