package one.chartsy.charting.financial;

import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.JLabel;

/**
 * Implements a fixed-width label.
 */
class FixedLabel extends JLabel {
  int width;

  public FixedLabel(int width) {
    this(width, LEFT);
  }

  public FixedLabel(int width, int align) {
    super(" ", align);
    this.width = width;
    setOpaque(true);
    setBackground(StockColors.BACKGROUND);
    setForeground(StockColors.FOREGROUND);
    if (align == LEFT)
      setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    else if (align == RIGHT)
      setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension dim = super.getPreferredSize();
    dim.width = this.width;
    return dim;
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }
}

