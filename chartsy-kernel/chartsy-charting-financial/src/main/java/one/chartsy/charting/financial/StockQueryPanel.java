package one.chartsy.charting.financial;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.util.Date;
import java.util.StringTokenizer;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A GUI panel that controls the loading of stock quotes.
 */
public class StockQueryPanel extends JPanel implements StockColors {
  /** The command sent when quotes shold be reloaded. */
  public static final String RELOAD_CMD = "Reload_CMD";

  static final int COLUMN_WIDTH = 7;
  JComboBox<StockQueryRange> dateRangeChooser;
  JComboBox<String> frequencyChooser;
  JTextField primarySymbol;
  JTextField secondarySymbols;

  /**
   * Creates a new <code>QueryPanel</code> object.
   */
  public StockQueryPanel() {
    setLayout(new FlowLayout(FlowLayout.LEFT, 6, 4));
    setOpaque(true);
    setBackground(HEADER_COLOR);
    // setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.darkGray));
    JLabel label = new JLabel("Symbol");
    label.setForeground(Color.black);
    add(label);
    primarySymbol = new JTextField(COLUMN_WIDTH);
    add(primarySymbol);

    label = new JLabel("vs.");
    label.setForeground(Color.black);
    add(label);
    secondarySymbols = new JTextField(COLUMN_WIDTH);
    add(secondarySymbols);

    dateRangeChooser = new JComboBox<StockQueryRange>(StockQueryRange.RANGES);
    dateRangeChooser.setActionCommand(RELOAD_CMD);
    dateRangeChooser.setSelectedIndex(4);
    dateRangeChooser.setToolTipText("Duration");
    add(dateRangeChooser);

    String[] items = new String[] { "Daily", "Weekly" };// , "Monthly"};
    frequencyChooser = new JComboBox<String>(items);
    frequencyChooser.setActionCommand(RELOAD_CMD);
    frequencyChooser.setToolTipText("Frequency");
    add(frequencyChooser);

    // setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Color.white));
    // setBorder(BorderFactory.createEtchedBorder());
    // setBorder(BorderFactory.createRaisedBevelBorder());
  }

  /**
   * Returns the data frequency for the query.
   * 
   * @return The data frequency.
   */
  public int getFrequency() {
    return frequencyChooser.getSelectedIndex();
  }

  /**
   * Returns the query range.
   * 
   * @return An array of two <code>Date</code> objects, representing the start
   *         and end date of the query.
   */
  public Date[] getQueryRange() {
    return ((StockQueryRange) dateRangeChooser.getSelectedItem()).getRange();
  }

  /**
   * Returns the contents of the "primarySymbol" text field.
   */
  public String getSymbol() {
    return primarySymbol.getText().trim().toUpperCase();
  }

  /**
   * Returns the secondary symbols. These symbols appear in the
   * "secondarySymbol" text field as a comma-separated list of symbols.
   * 
   * @return The secondary symbols, or <code>null</code> no secondary symbol is
   *         specified.
   */
  public String[] getSecondarySymbols() {
    StringTokenizer tok = new StringTokenizer(secondarySymbols.getText(), ",");
    int count = tok.countTokens();
    if (count == 0)
      return null;
    String[] res = new String[count];
    int i = 0;
    while (tok.hasMoreTokens()) {
      res[i++] = tok.nextToken().trim();
    }
    return res;
  }

  /**
   * Adds the specified listener to the query panel.
   */
  public void addActionListener(ActionListener l) {
    dateRangeChooser.addActionListener(l);
    frequencyChooser.addActionListener(l);
  }

}


