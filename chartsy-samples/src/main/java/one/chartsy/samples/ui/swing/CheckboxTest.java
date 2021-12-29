package one.chartsy.samples.ui.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public class CheckboxTest {

    public static void main(String[] args) throws UnsupportedLookAndFeelException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        JFrame f = new JFrame() {
            @Override
            public void paint(Graphics g) {
                ((Graphics2D) g).translate(-0.04, -0.04);
                super.paint(g);
            }
        };
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel p = new JPanel(new GridLayout(0, 1));
        p.add(new JCheckBox() {

            {
                setOpaque(false);
            }

            @Override
            public Insets getInsets() {
                Insets insets = super.getInsets();
                insets.bottom--;
                return insets;
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension preferredSize = super.getPreferredSize();
                System.out.println("PREF SIZE: " + preferredSize);
                preferredSize.height+=20;
                return preferredSize;
            }

            @Override
            public void paintAll(Graphics g) {
                g.setClip(null);
                //super.paintAll(g);
            }

            @Override
            public void paint(Graphics g) {
                System.out.println("CLIP: " + g.getClip());
                System.out.println("BOUNDS: " + getBounds());
                System.out.println("INSETS: " + getInsets());
                g.setClip(null);
                super.paint(g);

                Rectangle2D rect = new Rectangle2D.Double(0, 0, 119, 21);
                System.out.println("R1: " + rect);
                Point2D pt = ((Graphics2D) g).getTransform().transform(new Point2D.Double(rect.getWidth(), rect.getHeight()), null);
                System.out.println("TRA: " + pt);
                try {
                    System.out.println("INV: " + ((Graphics2D) g).getTransform().createInverse().transform(pt, null));
                } catch (NoninvertibleTransformException e) {
                    e.printStackTrace();
                }

            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                Paint oldPaint = g2.getPaint();
                g2.setColor(Color.yellow);
                g2.fillRect(0, 0, 500, 500);
                g2.setPaint(oldPaint);

                g.setClip(null);
                super.paintComponent(g);
                //super.paint(g);
            }
        });
        //p.add(new JCheckBox());
        //p.add(new JCheckBox());
        f.getContentPane().add(p, BorderLayout.SOUTH);
        f.pack();
        f.setVisible(true);
        f.setLocationRelativeTo(null);
    }
}
