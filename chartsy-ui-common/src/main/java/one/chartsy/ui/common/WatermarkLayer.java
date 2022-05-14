/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.common;

import javax.swing.*;
import javax.swing.plaf.LayerUI;
import java.awt.*;

public class WatermarkLayer<V extends Component> extends LayerUI<V> {

    private final ImageIcon image;
    private final AlphaComposite composite;

    public WatermarkLayer(ImageIcon image) {
        this(image, 0.10f);
    }

    public WatermarkLayer(ImageIcon image, float alpha) {
        this(image, AlphaComposite.SrcOver.derive(alpha));
    }

    public WatermarkLayer(ImageIcon image, AlphaComposite composite) {
        this.image = image;
        this.composite = composite;
    }

    public ImageIcon getImage() {
        return image;
    }

    public AlphaComposite getComposite() {
        return composite;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
        if (image != null)
            paintWatermark(image, (Graphics2D) g, c);
    }

    protected void paintWatermark(ImageIcon image, Graphics2D g2, JComponent c) {
        var old = g2.getComposite();
        try {
            g2.setComposite(composite);
            image.paintIcon(c, g2, (c.getWidth() - image.getIconWidth())/2, (c.getHeight() - image.getIconHeight())/2);
        } finally {
            g2.setComposite(old);
        }
    }
}
