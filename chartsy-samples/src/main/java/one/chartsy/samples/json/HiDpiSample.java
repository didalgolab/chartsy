package one.chartsy.samples.json;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.lang.reflect.InvocationTargetException;

public class HiDpiSample extends JComponent {

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.getContentPane().add(new HiDpiSample());
            f.setVisible(true);
            f.setExtendedState(JFrame.MAXIMIZED_BOTH);
            f.toBack();
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D)g;
        g2.setColor(Color.black);
        AffineTransform at = g2.getTransform();
        System.out.println(at.getTranslateX());
        System.out.println(at.getScaleX());
        System.out.println(at.getTranslateY());
        System.out.println(at.getScaleY());
        //g2.setStroke(new BasicStroke(1.0f));
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        int k = 0;
        //for (int k = 0; k <= 10; k++)
            for (double x = 1; x < w; x += 2.0 / 1.25)
                g2.draw(new Line2D.Double(x + 0.5, 0, x + 0.5 + 1.0, h));
        super.paintComponent(g);
    }
}
