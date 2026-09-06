/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class MouseListenerBindingTest {

    @Test
    void bindMouseListener_tracks_existing_added_removed_and_readded_children() throws Exception {
        verifyLifecycle(MouseListenerBinding::bindMouseListener, MouseListenerBindingTest::click);
    }

    @Test
    void bindMouseMotionListener_tracks_existing_added_removed_and_readded_children() throws Exception {
        verifyLifecycle(MouseListenerBinding::bindMouseMotionListener, component -> component.dispatchEvent(
                new MouseEvent(component, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 1, 1, 0, false)));
    }

    @Test
    void bindMouseWheelListener_tracks_existing_added_removed_and_readded_children() throws Exception {
        verifyLifecycle(MouseListenerBinding::bindMouseWheelListener, component -> component.dispatchEvent(
                new MouseWheelEvent(component, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, 1, 1,
                        0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1)));
    }

    @Test
    void bindMouseListener_does_not_register_unused_adapter_interfaces_or_descendants() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var container = new JPanel();
            var child = new JPanel();
            var descendant = new JPanel();
            child.add(descendant);
            container.add(child);
            var listener = new MouseAdapter() {};

            try (var _ = MouseListenerBinding.bindMouseListener(container, listener)) {
                assertThat(container.getMouseListeners()).contains(listener);
                assertThat(child.getMouseListeners()).contains(listener);
                assertThat(descendant.getMouseListeners()).doesNotContain(listener);
                assertThat(container.getMouseMotionListeners()).doesNotContain(listener);
                assertThat(child.getMouseMotionListeners()).doesNotContain(listener);
                assertThat(container.getMouseWheelListeners()).doesNotContain(listener);
                assertThat(child.getMouseWheelListeners()).doesNotContain(listener);
            }
        });
    }

    @Test
    void close_preserves_other_bindings() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var container = new JPanel();
            var child = new JPanel();
            container.add(child);
            var firstEvents = new ArrayList<Component>();
            var secondEvents = new ArrayList<Component>();
            var firstListener = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    firstEvents.add(event.getComponent());
                }
            };
            var secondListener = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    secondEvents.add(event.getComponent());
                }
            };

            try (var first = MouseListenerBinding.bindMouseListener(container, firstListener);
                 var second = MouseListenerBinding.bindMouseListener(container, secondListener)) {
                first.close();
                click(container);
                click(child);
                assertThat(firstEvents).isEmpty();
                assertThat(secondEvents).containsExactly(container, child);
            }
        });
    }

    @Test
    void close_during_child_addition_does_not_reattach_the_listener() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var container = new JPanel();
            var child = new JPanel();
            var events = new ArrayList<Component>();
            var listener = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    events.add(event.getComponent());
                }
            };
            var closeOnAdd = new ContainerAdapter() {
                private MouseListenerBinding binding;

                @Override
                public void componentAdded(ContainerEvent event) {
                    binding.close();
                }
            };
            container.addContainerListener(closeOnAdd);

            try (var binding = MouseListenerBinding.bindMouseListener(container, listener)) {
                closeOnAdd.binding = binding;
                container.add(child);
                click(container);
                click(child);
                assertThat(events).isEmpty();
            }
        });
    }

    private static void verifyLifecycle(BiFunction<Container, MouseAdapter, MouseListenerBinding> bind, Consumer<Component> dispatch) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var container = new JPanel();
            var existingChild = new JPanel();
            var addedChild = new JPanel();

            container.add(existingChild);
            var events = new ArrayList<Component>();
            var listener = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    events.add(event.getComponent());
                }

                @Override
                public void mouseMoved(MouseEvent event) {
                    events.add(event.getComponent());
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent event) {
                    events.add(event.getComponent());
                }
            };

            try (var binding = bind.apply(container, listener)) {
                container.add(addedChild);
                dispatch.accept(container);
                dispatch.accept(existingChild);
                dispatch.accept(addedChild);
                assertThat(events).containsExactly(container, existingChild, addedChild);

                events.clear();
                container.remove(existingChild);
                dispatch.accept(existingChild);
                assertThat(events).isEmpty();

                container.add(existingChild);
                dispatch.accept(existingChild);
                assertThat(events).containsExactly(existingChild);

                events.clear();
                binding.close();
                binding.close();
                var laterChild = new JPanel();
                container.add(laterChild);
                dispatch.accept(container);
                dispatch.accept(existingChild);
                dispatch.accept(addedChild);
                dispatch.accept(laterChild);
                assertThat(events).isEmpty();
                assertThat(container.getContainerListeners()).doesNotContain(binding);
            }
        });
    }

    private static void click(Component component) {
        component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                0, 1, 1, 1, false, MouseEvent.BUTTON1));
    }
}
