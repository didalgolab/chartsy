/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

@ServiceProvider(service = FrontEndSupport.class)
public class FrontEndSupport {

    public static FrontEndSupport getDefault() {
        return Lookup.getDefault().lookup(FrontEndSupport.class);
    }

    public <T> T execute(Callable<T> task) {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return task.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(task.call());
                } catch (Throwable t) {
                    error.set(t);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }

        Throwable t = error.get();
        if (t != null) {
            if (t instanceof RuntimeException re)
                throw re;
            if (t instanceof Error err)
                throw err;
            throw new RuntimeException(t);
        }
        return result.get();
    }

    public BufferedImage paintComponent(JComponent c) {
        Objects.requireNonNull(c, "JComponent");
        return execute(() -> {
            Dimension size = c.getSize();
            if (size.width <= 0 || size.height <= 0)
                size = c.getPreferredSize();
            if (size.width <= 0 || size.height <= 0)
                throw new IllegalArgumentException("Component has no renderable size");

            BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                paintComponent(c, g);
                return image;
            } finally {
                g.dispose();
            }
        });
    }

    public void paintComponent(JComponent c, Graphics2D g) {
        Objects.requireNonNull(c, "JComponent");
        Objects.requireNonNull(g, "Graphics2D");
        execute(() -> {
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);
            g.setColor(c.getBackground() != null ? c.getBackground() : Color.WHITE);
            g.fillRect(0, 0, c.getWidth(), c.getHeight());
            c.paint(g);
            return null;
        });
    }
}
