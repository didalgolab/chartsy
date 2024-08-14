/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.icons;

import org.openide.util.VectorIcon;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

public class ZoomIcon extends VectorIcon {
    public enum Type { PLUS, MINUS }

    private final Type type;

    public ZoomIcon(Type type) {
        super(16, 16);
        this.type = type;
    }

    @Override
    protected void paintIcon(Component c, Graphics2D g, int width, int height, double scaling) {
//        Paint old = g.getPaint();
//        g.setPaint(Color.white);
//        g.fillRect(0, 0, width, height);
//
//        g.setPaint(old);

        g.setColor(c.getForeground());
        g.setStroke(new BasicStroke(2f));
        int w1 = Math.round(width * 0.75f);
        g.drawOval(1, 1, w1, w1);

        int w = Math.round(width * 0.25f);
        g.setStroke(new BasicStroke(4f));
        g.drawLine(width - w, width - w, width - 3, width - 3);

        g.setStroke(new BasicStroke(3f));
        if (type == Type.PLUS) {
            g.setColor(new Color(143, 173, 27));
            g.drawLine(1 + w1 / 2, 1 + w1 / 4, 1 + w1 / 2, 1 + w1 - w1 / 4);
            g.drawLine(1 + w1 / 4, 1 + w1 / 2, 1 + w1 - w1 / 4, 1 + w1 / 2);
        } else if (type == Type.MINUS) {
            g.setColor(new Color(206, 92, 103));
            g.drawLine(1 + w1 / 4, 1 + w1 / 2, 1 + w1 - w1 / 4, 1 + w1 / 2);
        }
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            JPanel p = new JPanel(new FlowLayout());
            p.add(new JButton(new ZoomIcon(Type.PLUS)));
            p.add(new JButton(new ZoomIcon(Type.MINUS)));
            p.add(new JButton(new ZoomIcon(null)));
            p.add(new JButton("X"));

            JFrame f = new JFrame();
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.getContentPane().add(p);
            f.pack();
            f.setVisible(true);
        });

    }
}
