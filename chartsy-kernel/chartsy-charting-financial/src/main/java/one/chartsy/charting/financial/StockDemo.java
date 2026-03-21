package one.chartsy.charting.financial;

import one.chartsy.charting.AffineAxisTransformer;
import one.chartsy.charting.Axis;
import one.chartsy.charting.AxisTransformerException;
import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.ChartLayout;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.ColorEx;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DefaultStepsDefinition;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.Grid;
import one.chartsy.charting.LabelRenderer;
import one.chartsy.charting.Legend;
import one.chartsy.charting.LocalZoomAxisTransformer;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.Scale;
import one.chartsy.charting.TimeUnit;
import one.chartsy.charting.action.ChartAction;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DefaultDataSet;
import one.chartsy.charting.data.DefaultDataSource;
import one.chartsy.charting.event.AxisChangeEvent;
import one.chartsy.charting.event.AxisListener;
import one.chartsy.charting.event.AxisRangeEvent;
import one.chartsy.charting.event.ChartAreaEvent;
import one.chartsy.charting.event.ChartHighlightInteractionEvent;
import one.chartsy.charting.event.ChartInteractionEvent;
import one.chartsy.charting.event.ChartInteractionListener;
import one.chartsy.charting.event.ChartListener;
import one.chartsy.charting.event.DataSourceEvent;
import one.chartsy.charting.event.DataSourceListener;
import one.chartsy.charting.interactors.ChartLocalPanInteractor;
import one.chartsy.charting.interactors.ChartLocalReshapeInteractor;
import one.chartsy.charting.interactors.ChartPanInteractor;
import one.chartsy.charting.interactors.ChartXScrollInteractor;
import one.chartsy.charting.renderers.HiLoChartRenderer;
import one.chartsy.charting.renderers.PolylineChartRenderer;
import one.chartsy.charting.renderers.SingleAreaRenderer;
import one.chartsy.charting.renderers.SingleBarRenderer;
import one.chartsy.charting.renderers.SingleHiLoRenderer;
import one.chartsy.charting.renderers.SinglePolylineRenderer;
import one.chartsy.charting.renderers.SingleStairRenderer;
import one.chartsy.charting.util.DoubleArray;
import one.chartsy.charting.financial.indicator.BollingerBandsIndicator;
import one.chartsy.charting.financial.indicator.MACDIndicator;
import one.chartsy.charting.financial.indicator.MovingAverageIndicator;
import one.chartsy.charting.financial.indicator.PriceChannelIndicator;
import one.chartsy.charting.financial.indicator.RSIIndicator;
import one.chartsy.charting.financial.indicator.StochasticIndicator;
import one.chartsy.charting.financial.indicator.TechnicalIndicator;
import one.chartsy.charting.financial.indicator.VolumeIndicator;
import one.chartsy.charting.financial.indicator.WilliamsRIndicator;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

/**
 * A simple application that displays stock information. The sample loads quotes
 * from the Yahoo finance Web pages and displays them in two distinct charts on
 * which you can perform interactions. The displayed information is the stock
 * prices and the exchanged volume. The data is retrieved by sending a formatted
 * <i>cgi query</i> to the Yahoo Web site, and the result is decoded using a
 * custom <code>DataReader</code> object. This reader (an instance of
 * <code>CSVDataReader</code>) extracts the data from the input stream and
 * initializes the data source with the corresponding data sets.
 */
public class StockDemo extends AbstractChartExample implements ActionListener, StockColors {
  private static final int MAIN_WINDOW_DEFAULT_WIDTH = 1280;
  private static final int MAIN_WINDOW_DEFAULT_HEIGHT = 740;
  private static final int DEFAULT_VISIBLE_BAR_COUNT = 140;
  private static final double CANDLE_WIDTH_PERCENT = 74.0;

  /** The number of lower charts. */
  private static final int LOWER_CHART_COUNT = 2;

  private static final int RIGHT_MARGIN = 6;
  private static final int LEGEND_MARGIN = 6;

  private static final String HIGHLIGHT_STATUS_KEY = "HIGHLIGHT_STATUS_KEY";
  private static final Font DEFAULT_FONT = new Font("Dialog", Font.PLAIN, 10);
  private static final Color LEGEND_BACKGROUND = HEADER_COLOR;


    public static final int LINE = 0;
  public static final int AREA = 1;
  public static final int BAR = 2;
  public static final int STAIR = 3;
  public static final int HLOC = 4;
  public static final int CANDLE = 5;

  public static final String LINE_CMD = "Line_CMD";
  public static final String AREA_CMD = "Area_CMD";
  public static final String BAR_CMD = "Bar_CMD";
  public static final String STAIR_CMD = "Stair_CMD";
  public static final String HLOC_CMD = "HLOC_CMD";
  public static final String CANDLE_CMD = "Candle_CMD";
  public static final String LOCALZOOM_CMD = "LocalZoom_CMD";
  public static final String THRESHOLD_CMD = "Threshold_CMD";

  /**
   * The threshold beyond which quotes are displayed with an overview
   * representation mode. This constant is equal to the approximate duration of
   * 3 months for daily data and one year for weekly data.
   */
  public static final double OVERVIEW_THRESHOLD = 500;

  static {
    //List<Color> colorList = Color.getDefaultColors();
    //colorList.clear();
    //colorList.addAll(Arrays.asList(DEFAULT_COLORS));
  }

  // == Chart related data members
  StockDataSource stockDS;
  Chart mainChart;
  Chart[] lowerCharts;

  List<TechnicalIndicator> upperIndicators = new ArrayList<TechnicalIndicator>();
  TechnicalIndicator[] lowerIndicators;

  /** The overview renderer for the primary symbol. */
  ChartRenderer overviewRenderer;
  /** The detailed renderer for the primary symbol. */
  ChartRenderer detailedRenderer;
  /** The renderer for the seconary symbols. */
  ChartRenderer secondaryRenderer;

  /**
   * Stores all the interactors that should be set on the upper chart. The first
   * array contains the default interactors, while the second array holds the
   * interactors when the local zoom is active.
   */
  ChartInteractor[][] upperInteractors = new ChartInteractor[2][];
  ChartInteractor[][][] lowerInteractors = new ChartInteractor[2][][];

  AxisZoomHistory zoomHistory = new AxisZoomHistory();
  Highlighter highlighter;
  boolean antiAliasing = false;

  // == UI related data members
  private StockQueryPanel queryPanel;
  private JButton refreshButton;
  private JToggleButton thresholdButton;
  private ChartAction backAction;
  private ChartAction fwdAction;
  private ChartAction fitAction;
  private LocalZoomAction zoomInAction;
  private LocalZoomAction zoomOutAction;
  private List<IndicatorAction> upperIndicatorActions;
  private List<IndicatorAction>[] lowerIndicatorActions;

  private ThresholdLines threshold;
  private ChartMessage loadingMessage;

  private NumberFormat numFmt = new DecimalFormat(".###");
  private final DateFormat dateFmt = DateFormat.getDateInstance(DateFormat.SHORT);
  private JFrame topFrame;
  private JToolBar toolBar;
  private QuoteDisplayPanel quoteDisplay;
  private PercentDisplayer percentDisplayer;
  private final Scale sharedTimeScale = new Scale();

  /**
   * Initializes a new <code>TableModelDemo</code> object.
   **/
  @Override
  public void init(Container container) {
    super.init(container);
    container.setLayout(new BorderLayout());
    container.setBackground(BACKGROUND);

    numFmt = NumberFormat.getNumberInstance();
    numFmt.setMaximumFractionDigits(3);

    // Create the stock data source to read the result of the query.
    URL defaultDataURL = null;

    try {
      defaultDataURL = getClass().getResource("/default.csv");
    } catch (Exception x) {
      x.printStackTrace();
    }
    stockDS = new StockDataSource(defaultDataURL);
    // == Create the main chart.
    mainChart = createMainChart();
    percentDisplayer = new PercentDisplayer();
    percentDisplayer.setActive(false);
    setOverviewRepresentation(LINE);
    setDetailedRepresentation(CANDLE);
    secondaryRenderer = new PolylineChartRenderer();
    mainChart.addRenderer(secondaryRenderer);

    // == Create indicator charts.
    lowerCharts = createLowerCharts();
    lowerIndicators = new TechnicalIndicator[lowerCharts.length];

    // == Create the interactors.
    createInteractors();

    // == Create and add the Query and Config panels.
    queryPanel = new StockQueryPanel();
    queryPanel.primarySymbol.setText(StockDataSource.DEFAULT_SYMBOL);
    queryPanel.addActionListener(this);

    JPanel top = new JPanel(new BorderLayout());
    top.setOpaque(true);
    top.setBackground(HEADER_COLOR);
    toolBar = createToolBar();
    top.add(toolBar, BorderLayout.NORTH);
    top.add(queryPanel, BorderLayout.CENTER);
    container.add(top, BorderLayout.NORTH);
    top.getRootPane().setDefaultButton((JButton) toolBar.getComponentAtIndex(0));

    // Install interactors
    installDefaultInteractors();

    JPanel center = new JPanel(new java.awt.GridBagLayout());
    center.setOpaque(false);
    java.awt.GridBagConstraints constraints = new java.awt.GridBagConstraints();
    constraints.gridx = 0;
    constraints.weightx = 1.0;
    constraints.fill = java.awt.GridBagConstraints.BOTH;
    constraints.insets = new java.awt.Insets(0, 0, 0, 0);

    constraints.gridy = 0;
    constraints.weighty = 0.72;
    center.add(mainChart, constraints);

    constraints.gridy = 1;
    constraints.weighty = 0.16;
    center.add(lowerCharts[0], constraints);

    constraints.gridy = 2;
    constraints.weighty = 0.12;
    center.add(lowerCharts[1], constraints);

    container.add(center, BorderLayout.CENTER);
    // Create and install the menu bar.
    setMenuBar(createMenuBar());

    setAntiAliasing(true);

    // Load the default data.
    loadDefaultData();
  }

  /**
   * Returns the data source that holds the stock data.
   */
  public StockDataSource getStockDataSource() {
    return stockDS;
  }

  // --------------------------------------------------------------------------
  // - Indicators
  /** Sets a lower indicator. */
  protected void setLowerIndicator(int idx, TechnicalIndicator indicator) {
    if (lowerIndicators[idx] == indicator)
      return;
    if (lowerIndicators[idx] != null)
      lowerIndicators[idx].detach();
    if (indicator != null)
      indicator.attach(lowerCharts[idx]);
    lowerIndicators[idx] = indicator;
  }

  /** Adds an upper indicator. */
  protected void addUpperIndicator(TechnicalIndicator indicator) {
    if (indicator != null)
      indicator.attach(mainChart);
    upperIndicators.add(indicator);
  }

  /** Removes an upper indicator. */
  protected void removeUpperIndicator(TechnicalIndicator indicator) {
    if (!upperIndicators.contains(indicator))
      return;
    if (indicator != null)
      indicator.detach();
    upperIndicators.remove(indicator);
  }

  /**
   * Creates the highlight component. This component will be set in the header
   * of the specified chart.
   */
  protected JComponent createHighlightComponent(Chart chart, JComponent leftComponent, int highlightStatusWidth) {
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, RIGHT_MARGIN));
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    panel.setBackground(HEADER_COLOR);

    if (leftComponent != null) {
      panel.add(leftComponent);
      panel.add(Box.createHorizontalStrut(4));
    }
    panel.add(Box.createHorizontalGlue());
    JLabel status = new JLabel(" ", JLabel.RIGHT);
    status.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
    status.setFont(DEFAULT_FONT);

    Dimension size = status.getPreferredSize();
    size.width = highlightStatusWidth;
    status.setPreferredSize(size);
    status.setMinimumSize(size);
    status.setMaximumSize(size);
    status.setForeground(FOREGROUND);
    status.setBackground(BACKGROUND);
    status.setOpaque(true);
    chart.putClientProperty(HIGHLIGHT_STATUS_KEY, status);
    panel.add(status);

    return panel;
  }

  /**
   * Creates the main chart. This chart displays the stock data and the upper
   * indicators.
   */
  protected Chart createMainChart() {
    Chart chart = createChart();
    chart.getXAxis().setVisibleRange(0, 10);
    chart.getYAxis(0).setVisibleRange(0, 100);
    chart.getYScale(0).setLabelFont(new Font("Dialog", Font.PLAIN, 11));
    sharedTimeScale.setLabelFont(new Font("Dialog", Font.PLAIN, 11));
    sharedTimeScale.setSkipLabelMode(Scale.ADAPTIVE_SKIP);
    chart.setXGrid(new SharedGrid(sharedTimeScale, GRID_COLOR));
    chart.setXScale(null);
    chart.getChartArea().setMargins(new java.awt.Insets(0, 0, 0, RIGHT_MARGIN));
    chart.getChartArea().setPlotRectIncludingAnnotations(false);
    // Add an axis listener to toggle the visibility of the
    // quote renderers/quote overview.
    chart.getXAxis().addAxisListener(new AxisListener() {
      double oldZoomfactor;
      double oldLen = 0;
      boolean oldAntiAliasing;

      @Override
      public void axisRangeChanged(AxisRangeEvent evt) {
        if (evt.isChangedEvent() && !evt.isAdjusting() && evt.isVisibleRangeEvent()) {
          updateDisplay(evt.getAxis());
        } else if (evt.isVisibleRangeEvent() && !evt.isChangedEvent()) {
          double min = evt.getNewMin();
          double max = evt.getNewMax();
          if (max - min < StockUtil.MIN_XLEN) {
            double mid = (max + min) / 2;
            evt.setNewMax(mid + StockUtil.MIN_XLEN / 2);
            evt.setNewMin(mid - StockUtil.MIN_XLEN / 2);
          }
        }
      }

      @Override
      public void axisChanged(AxisChangeEvent evt) {
        if (evt.getType() == AxisChangeEvent.ADJUSTMENT_CHANGE) {
          if (evt.isAdjusting()) {
            if (StockDemo.this.antiAliasing) {
              StockDemo.this.setAntiAliasing(false);
              oldAntiAliasing = true;
            }
          } else {
            if (oldAntiAliasing) {
              StockDemo.this.setAntiAliasing(true);
              oldAntiAliasing = false;
            }
            updateDisplay(evt.getAxis());
          }
        } else if (evt.getType() == AxisChangeEvent.TRANSFORMER_CHANGE) {
          LocalZoomHandler lzh = LocalZoomHandler.get(mainChart, -1);
          if (lzh == null)
            return;
          if (oldZoomfactor != lzh.getTransformer().getZoomFactor()) {
            zoomInAction.computeEnabled();
            zoomOutAction.computeEnabled();
            oldZoomfactor = lzh.getTransformer().getZoomFactor();
          }
        }
      }

      private void updateDisplay(Axis axis) {
        // Check if the zoom level has changed and call the
        // xRangeChanged() method.
        DataInterval xRange = mainChart.getXAxis().getVisibleRange();
        double newLen = xRange.getLength();
        boolean zoom = (oldLen == 0) || (Math.abs(newLen - oldLen) / oldLen > .01);
        StockDemo.this.xRangeChanged(zoom);
        oldLen = newLen;
      }
    });

    quoteDisplay = new QuoteDisplayPanel();
    Legend legend = createLegend();
    attachLegend(chart, legend);
    return chart;  }

  /**
   * Handles the display of percentage variation on a secondary y-scale.
   */
  class PercentDisplayer implements AxisListener, DataSourceListener {

    /** The transform that maps stock prices into percentage variation. */
    AffineAxisTransformer transform = new AffineAxisTransformer(1., 0);
    boolean active = false;
    boolean visible = false;
    Scale percentScale;
    Grid percentGrid;

    PercentDisplayer() {

      // Add an y-axis
      Axis yAxis = mainChart.addYAxis(false, false);
      yAxis.setTransformer(transform);
      yAxis.synchronizeWith(mainChart.getYAxis(0), true);

      // A scale with custom label formatting
      percentScale = new Scale() {
        @Override
        public String computeLabel(double v) {
          String res = super.computeLabel(v);
          return (getPercentVariation(v) > 0) ? ('+' + res + '%') : (res + '%');
        }
      };
      percentScale.setMajorTickVisible(false);
      percentScale.setStepUnit(null, Double.valueOf(0));

      // A grid that draws a single grid line at 0% variation.
      percentGrid = new Grid() {
        @Override
        public void draw(Graphics g) {
          if (getChart() == null)
            return;
          Axis axis = getAxis();
          if (axis.getTVisibleRange().isInside(0)) {
            try {
              DoubleArray values = new DoubleArray();
              values.add(transform.inverse(0));
              draw(g, values, true);
            } catch (AxisTransformerException x) {
            }
          }
        }
      };
      Stroke stroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 10.0f,
          new float[] { 10.f, 6.f }, 0.0f);
      percentGrid.setMajorStroke(stroke);
      percentGrid.setMajorPaint(Color.white);
      setActive(true);
    }

    /**
     * Returns the percent variation corresponding to the specified stock price.
     */
    public double getPercentVariation(double price) {
      try {
        return transform.apply(price);
      } catch (AxisTransformerException x) {
        return 0;
      }
    }

    @Override
    public void axisRangeChanged(AxisRangeEvent evt) {
      if (evt.isChangedEvent() && evt.isVisibleRangeEvent())
        update();
    }

    @Override
    public void axisChanged(AxisChangeEvent evt) {
    }

    @Override
    public void dataSourceChanged(DataSourceEvent evt) {
      update();
    }

    private void update() {
      DataSet closeDS = getStockDataSource().getCloseDataSet();
      if (closeDS != null) {
        int firstIdx = (int) Math.ceil(mainChart.getXAxis().getVisibleMin());
        if (firstIdx < closeDS.size()) {
          double v = closeDS.getYData(firstIdx);
          transform.setDefinition(100. / v, -100.);
          setVisible(true);
          return;
        }
      }
      transform.setDefinition(1., 0.);
      setVisible(false);
    }

    /**
     * Shhows or hide the percentage variation.
     */
    public void setVisible(boolean visible) {
      if (this.visible == visible)
        return;
      this.visible = visible;
      if (visible) {
        mainChart.getChartArea().setMargins(null);
        mainChart.setYScale(1, percentScale);
        mainChart.setYGrid(1, percentGrid);
      } else {
        mainChart.getChartArea().setMargins(new java.awt.Insets(0, 0, 0, RIGHT_MARGIN));
        mainChart.setYScale(1, null);
        mainChart.setYGrid(1, null);
      }
    }

    /**
     * Activates or desactivates the display of percentage variation.
     */
    public void setActive(boolean active) {
      if (this.active == active)
        return;
      this.active = active;
      if (active) {
        update();
        mainChart.getXAxis().addAxisListener(this);
        getStockDataSource().addDataSourceListener(this);
        setVisible(true);
      } else {
        setVisible(false);
        mainChart.getXAxis().removeAxisListener(this);
        getStockDataSource().removeDataSourceListener(this);
      }
    }
  }

  /** Creates the charts displaying lower indicators. */
  protected Chart[] createLowerCharts() {
    Chart[] charts = new Chart[LOWER_CHART_COUNT];
    for (int i = 0; i < LOWER_CHART_COUNT; ++i) {
      charts[i] = createLowerChart(i == LOWER_CHART_COUNT - 1);
    }
    return charts;
  }

  /** Creates a lower chart. */
  private Chart createLowerChart(boolean showXAxis) {
    Chart chart = createChart();
    chart.synchronizeAxis(mainChart, Axis.X_AXIS, true);
    chart.setBorder(BorderFactory.createEmptyBorder());
    chart.getChartArea().setTopMargin(0);
    chart.setDataRangePolicy(new one.chartsy.charting.DefaultDataRangePolicy() {
      @Override
      protected boolean shouldAdjust(Chart chart, Axis axis) {
        return false;
      }
    });
    chart.getYScale(0).setLabelFont(new Font("Dialog", Font.PLAIN, 11));
    if (!showXAxis) {
      chart.setXScale(null);
      chart.getChartArea().setMargins(new java.awt.Insets(0, 0, 0, RIGHT_MARGIN));
    } else {
      chart.setXScale(sharedTimeScale);
      chart.getXScale().setVisible(true);
      chart.getXScale().setMajorTickVisible(true);
      chart.getXScale().setSkipLabelMode(Scale.ADAPTIVE_SKIP);
      chart.getXScale().setLabelFont(new Font("Dialog", Font.PLAIN, 11));
    }
    chart.setXGrid(new SharedGrid(sharedTimeScale, GRID_COLOR));
    Legend legend = createLegend();
    attachLegend(chart, legend);
    return chart;

  }

  /** Creates the list of upper indicators. */  protected List<TechnicalIndicator> createUpperIndicators() {
    List<TechnicalIndicator> list = new ArrayList<TechnicalIndicator>();
    list.add(new BollingerBandsIndicator(stockDS));
    list.add(new PriceChannelIndicator(stockDS));
    list.add(MovingAverageIndicator.createSMA(stockDS, 20));
    list.add(MovingAverageIndicator.createSMA(stockDS, 40));
    list.add(MovingAverageIndicator.createEMA(stockDS, 20));
    list.add(MovingAverageIndicator.createEMA(stockDS, 40));
    return list;
  }

  /** Creates the list of lower indicators. */
  protected List<TechnicalIndicator> createLowerIndicators() {
    List<TechnicalIndicator> list = new ArrayList<TechnicalIndicator>();
    list.add(new VolumeIndicator(stockDS));
    list.add(new RSIIndicator(stockDS, 14));
    list.add(MACDIndicator.createMACD(stockDS));
    list.add(new WilliamsRIndicator(stockDS, 14));
    list.add(StochasticIndicator.createFastStochastic(stockDS));
    list.add(StochasticIndicator.createSlowStochastic(stockDS));
    return list;

  }

  /** Initializes the actions associated with indicators. */
  @SuppressWarnings("unchecked")
  private void initIndicatorActions() {
    List<TechnicalIndicator> list = createUpperIndicators();
    Iterator<TechnicalIndicator> ite = list.iterator();
    upperIndicatorActions = new ArrayList<>(list.size());
    while (ite.hasNext()) {
      TechnicalIndicator indicator = ite.next();
      UpperIndicatorAction action = new UpperIndicatorAction(indicator);
      action.updateEnabled();
      upperIndicatorActions.add(action);
    }

    lowerIndicatorActions = new ArrayList[lowerCharts.length];
    for (int i = 0; i < lowerIndicatorActions.length; ++i) {
      lowerIndicatorActions[i] = new ArrayList<IndicatorAction>();
      ite = createLowerIndicators().iterator();
      while (ite.hasNext()) {
        TechnicalIndicator indicator = ite.next();
        LowerIndicatorAction action = new LowerIndicatorAction(indicator, i);
        // action.updateEnabled();
        lowerIndicatorActions[i].add(action);
      }
    }
  }

  // --------------------------------------------------------------------------
  // - Menus & Toolbar

  /** Creates the toolbar. */
  @Override
  protected JToolBar createToolBar() {
    JToolBar toolbar = new JToolBar();
    toolbar.setFloatable(false);
    populateToolBar(toolbar);
    return toolbar;
  }

  /** Creates the menu bar. */
  protected JMenuBar createMenuBar() {
    JMenuBar menu = new JMenuBar();
    menu.add(createDisplayMenu());
    menu.add(createIndicatorsMenu());
    menu.add(createAuthenticationMenu());
    return menu;
  }

  private JMenuItem getOverviewMenu() {
    return getMenuBar().getMenu(0).getItem(1);
  }

  private JMenuItem getDetailedMenu() {
    return getMenuBar().getMenu(0).getItem(0);
  }

  /** Creates the menu for display settings. */
  protected JMenu createDisplayMenu() {
    JMenu menu = new JMenu("Display");
    JMenuItem item = null;

    JMenu subMenu = new JMenu("Overview Representation");
    menu.add(subMenu);
    ButtonGroup group = new ButtonGroup();
    item = new JRadioButtonMenuItem("Line", true);
    item.setActionCommand(LINE_CMD);
    group.add(item);
    item.addActionListener(this);

    subMenu.add(item);
    item = new JRadioButtonMenuItem("Area");
    item.setActionCommand(AREA_CMD);
    group.add(item);
    item.addActionListener(this);
    subMenu.add(item);

    item = new JRadioButtonMenuItem("Stair");
    item.setActionCommand(STAIR_CMD);
    group.add(item);
    item.addActionListener(this);
    subMenu.add(item);

    item = new JRadioButtonMenuItem("Bar");
    item.setActionCommand(BAR_CMD);
    group.add(item);
    item.addActionListener(this);
    subMenu.add(item);

    subMenu = new JMenu("Detailed Representation");
    menu.add(subMenu);
    group = new ButtonGroup();
    item = new JRadioButtonMenuItem("Candle", true);
    item.setActionCommand(CANDLE_CMD);
    group.add(item);
    item.addActionListener(this);

    subMenu.add(item);
    item = new JRadioButtonMenuItem("HLOC");
    item.setActionCommand(HLOC_CMD);
    group.add(item);
    item.addActionListener(this);
    subMenu.add(item);

    menu.addSeparator();
    item = new JCheckBoxMenuItem("Anti-aliasing", true);
    item.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent evt) {
        setAntiAliasing(((JCheckBoxMenuItem) evt.getSource()).isSelected());
      }
    });
    menu.add(item);
    item = new JCheckBoxMenuItem("Logarithmic Price Axis");
    item.setSelected(true);
    item.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent evt) {
        boolean log = ((JCheckBoxMenuItem) evt.getSource()).isSelected();
        if (log)
          mainChart.getYAxis(0).setTransformer(new ExponentAxisTransformer(.5));
        else
          mainChart.getYAxis(0).setTransformer(null);
      }
    });
    menu.add(item);
    mainChart.getYAxis(0).setTransformer(new ExponentAxisTransformer(.5));

    return menu;
  }

  /** Creates the menu that controls technical indicators. */
  protected JMenu createIndicatorsMenu() {
    // Initialize the actions for setting/removing the indicators.
    initIndicatorActions();

    JMenu menu = new JMenu("Indicators");

    JMenu submenu = createUpperIndicatorMenu();
    menu.add(submenu);

    menu.addSeparator();

    for (int i = 0; i < lowerCharts.length; ++i) {
      submenu = createLowerIndicatorMenu(i);
      menu.add(submenu);
    }

    return menu;
  }
  
  /** Creates the menu and action which allows the user's API key to be input. */
  private JMenu createAuthenticationMenu() {
        JMenu menu = new JMenu("Authentication");
        JMenuItem setAPIKeyItem = new JMenuItem(new AbstractAction("Set API Key...") {
                @Override
                public void actionPerformed(ActionEvent e) {
                        String apiKey = (String) JOptionPane.showInputDialog(
                                topFrame,
                                "Enter your barchart.com API key from the Authentication menu:",
                                "Set API Key",
                                JOptionPane.PLAIN_MESSAGE,
                                null,
                                null,
                                stockDS.getAPIKey());
                        stockDS.setAPIKey(apiKey == null ? "" : apiKey);
                }
        });
        menu.add(setAPIKeyItem);
        return menu;
  }

  /** Creates the menu that controls upper indicators. */
  protected JMenu createUpperIndicatorMenu() {
    JMenu menu = new JMenu("Upper Indicators");
    Iterator<IndicatorAction> ite = upperIndicatorActions.iterator();
    while (ite.hasNext()) {
      IndicatorAction action = ite.next();
      JCheckBoxMenuItem item = new JCheckBoxMenuItem(action);
      menu.add(item);
    }
    return menu;
  }

  /** Creates the menu that controls lower indicators. */
  protected JMenu createLowerIndicatorMenu(int idx) {
    String desc = "Lower Indicator" + (idx + 1);
    JMenu menu = new JMenu(desc);
    ButtonGroup group = new ButtonGroup();
    int i = 0;
    Iterator<IndicatorAction> ite = lowerIndicatorActions[idx].iterator();
    while (ite.hasNext()) {
      IndicatorAction action = ite.next();
      JRadioButtonMenuItem item = new JRadioButtonMenuItem(action);
      if (i == idx) {
        setLowerIndicator(idx, action.getIndicator());
        item.setSelected(true);
      }
      group.add(item);
      menu.add(item);
      ++i;
    }

    return menu;
  }

  /**
   * Populates the specified toolbar. This method is called by the
   * <code>createToolBar()</code> method.
   */
  @Override
  protected void populateToolBar(JToolBar toolbar) {
    // == Refresh action.
    ChartAction refreshAction = new ChartAction("Reload", null, null, "Reload Charts", null) {
      @Override
      public void actionPerformed(ActionEvent evt) {
        String symbol = queryPanel.getSymbol();
        Date[] range = queryPanel.getQueryRange();
        if (symbol == null || symbol.length() == 0) {
          String msg = "Please enter a ticker symbol.";
          JOptionPane.showMessageDialog(topFrame, msg, "Loading Error", JOptionPane.WARNING_MESSAGE);
        } else if (stockDS == null || stockDS.getAPIKey().isEmpty()) {
          JOptionPane.showMessageDialog(
                          topFrame,
                          "Please enter your API key from the Authentication menu.",
                          "Loading Error",
                          JOptionPane.WARNING_MESSAGE);

        } else
          loadQuotes(symbol, range[0], range[1], queryPanel.getFrequency());
      }
    };
    refreshAction.setIcon(getClass(), "refresh.gif");
    refreshAction.setChart(mainChart);
    refreshButton = addAction(toolbar, refreshAction);
    Dimension size = refreshButton.getPreferredSize();
    toolbar.addSeparator();

    // == Zoom History navigation.
    backAction = new BackwardAction(zoomHistory);
    backAction.setChart(mainChart);
    addAction(toolbar, backAction);
    fwdAction = new ForwardAction(zoomHistory);
    fwdAction.setChart(mainChart);
    addAction(toolbar, fwdAction);
    fitAction = new ChartAction("Fit", null, null, "Reset Zoom", null) {
      @Override
      public void actionPerformed(ActionEvent evt) {
        StockUtil.performAnimatedZoom(mainChart, 0, mainChart.getXAxis().getDataRange(), StockUtil.ANIMATION_STEPS);
        //super.actionPerformed(evt);
        zoomHistory.reset();
        zoomHistory.add(mainChart.getXAxis().getVisibleRange());
      }
    };
    fitAction.setIcon(getClass(), "fit.gif");
    fitAction.setChart(mainChart);
    addAction(toolbar, fitAction);
    toolbar.addSeparator();

    // == Local zoom
    toolbar.add(createToggleButton("lzoom.png", "lzoom_sel.png", size, LOCALZOOM_CMD, "Local Zoom"));

    zoomInAction = new LocalZoomAction(2.d, false);
    zoomInAction.setChart(mainChart);
    zoomOutAction = new LocalZoomAction(2.f, true);
    zoomOutAction.setChart(mainChart);
    toolbar.addSeparator();

    // == Price threshold
    thresholdButton = createToggleButton("threshold.png", "threshold_sel.png", size, THRESHOLD_CMD, "Threshold Lines");
    thresholdButton.setSelected(false);
    toolbar.add(thresholdButton);

    toolbar.setBackground(HEADER_COLOR);
    toolbar.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
  }

  // --------------------------------------------------------------------------
  /**
   * Handles <code>ActionEvent</code> events that occur on the registered
   * listeners.
   */
  @Override
  public void actionPerformed(ActionEvent evt) {
    if (evt.getActionCommand().equals(StockQueryPanel.RELOAD_CMD)) {
      String symbol = queryPanel.getSymbol();
      if (symbol == null || symbol.length() == 0)
        return;
      Date[] range = queryPanel.getQueryRange();
      loadQuotes(symbol, range[0], range[1], queryPanel.getFrequency());
    } else if (evt.getActionCommand().equals(LINE_CMD)) {
      setOverviewRepresentation(LINE);
    } else if (evt.getActionCommand().equals(AREA_CMD)) {
      setOverviewRepresentation(AREA);
    } else if (evt.getActionCommand().equals(STAIR_CMD)) {
      setOverviewRepresentation(STAIR);
    } else if (evt.getActionCommand().equals(BAR_CMD)) {
      setOverviewRepresentation(BAR);
    } else if (evt.getActionCommand().equals(HLOC_CMD)) {
      setDetailedRepresentation(HLOC);
    } else if (evt.getActionCommand().equals(CANDLE_CMD)) {
      setDetailedRepresentation(CANDLE);
    } else if (evt.getActionCommand().equals(LOCALZOOM_CMD)) {
      setLocalZoom(((JToggleButton) evt.getSource()).isSelected());
    } else if (evt.getActionCommand().equals(THRESHOLD_CMD)) {
      setThreshold(((JToggleButton) evt.getSource()).isSelected());
    }
  }

  /** Toggles the local zoom. */
  private void setLocalZoom(boolean set) {
    if (set) {
      double middle = mainChart.getXAxis().getVisibleRange().getMiddle();
      double length = mainChart.getXAxis().getVisibleRange().getLength();
      Axis axis = mainChart.getXAxis();
      LocalZoomAxisTransformer t = LocalZoomAxisTransformer.create(axis, middle - length / 30, middle + length / 30, 3,
          false);
      axis.setTransformer(t);
      LocalZoomHandler lzh = LocalZoomHandler.set(mainChart, lowerCharts, t);
      lzh.setStartAnnotation(null);
      lzh.setEndAnnotation(null);
      sharedTimeScale.setSkipLabelMode(Scale.ADAPTIVE_SKIP);
      installLocalInteractors();
    } else {
      LocalZoomHandler.unset(mainChart, -1);
      sharedTimeScale.setSkipLabelMode(Scale.CONSTANT_SKIP);
      installDefaultInteractors();
    }
  }

  /** Toggles the price threshold. */
  private synchronized void setThreshold(boolean set) {
    int idx = toolBar.getComponentIndex(thresholdButton);
    if (set) {
      if (threshold == null) {
        Color upperColor = new Color(120, 220, 120);
        Color midColor = new Color(233, 150, 122);
        Color lowerColor = new Color(205, 92, 92);
        threshold = new ThresholdLines(FOREGROUND, BACKGROUND, lowerColor, midColor, upperColor);
      }
      ThresholdLines.set(threshold, mainChart, 0);
      toolBar.add(threshold.getRangeSlider(), idx + 1);
      overviewRenderer.setRenderingHint(threshold);
      detailedRenderer.setRenderingHint(threshold);
    } else {
      ThresholdLines threshold = ThresholdLines.remove(mainChart, 0);
      if (threshold != null)
        toolBar.remove(threshold.getRangeSlider());
      overviewRenderer.setRenderingHint(null);
      detailedRenderer.setRenderingHint(null);
    }
    toolBar.revalidate();
    toolBar.repaint();
  }

  /** Installs the interactors when the local zoom is active. */
  private void installLocalInteractors() {
    fitAction.setChart(null);
    backAction.setChart(null);
    fwdAction.setChart(null);
    zoomInAction.setChart(mainChart);
    zoomOutAction.setChart(mainChart);
    mainChart.setInteractors(upperInteractors[1]);
    for (int i = 0; i < lowerCharts.length; ++i) {
      lowerCharts[i].setInteractors(lowerInteractors[1][i]);
    }
  }

  /** Installs the default interactors. */
  private void installDefaultInteractors() {
    fitAction.setChart(mainChart);
    backAction.setChart(mainChart);
    fwdAction.setChart(mainChart);
    zoomInAction.setChart(null);
    zoomOutAction.setChart(null);
    mainChart.setInteractors(upperInteractors[0]);
    for (int i = 0; i < lowerCharts.length; ++i) {
      lowerCharts[i].setInteractors(lowerInteractors[0][i]);
    }
  }

  /** Toggles anti-aliasing for the charts. */
  public void setAntiAliasing(boolean b) {
    if (antiAliasing == b)
      return;
    mainChart.setAntiAliasing(b);
    mainChart.getChartArea().repaint();
    for (int i = 0; i < lowerCharts.length; ++i) {
      lowerCharts[i].setAntiAliasing(b);
      lowerCharts[i].getChartArea().repaint();
    }
    antiAliasing = b;
  }

  /** Invoked before data is loaded. */
  protected void startLoading() {
    mainChart.getCoordinateSystem(0).getVisibleWindow();
    if (loadingMessage == null) {
      LabelRenderer label = new LabelRenderer();
      label.setFont(new Font("Dialog", Font.BOLD, 18));
      label.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(FOREGROUND),
          BorderFactory.createEmptyBorder(8, 8, 8, 8)));
      label.setOpaque(true);
      label.setBackground(ColorEx.setAlpha(BACKGROUND, .8f));
      loadingMessage = new ChartMessage("LOADING ...", label);
    }
    mainChart.addChartDrawListener(loadingMessage);
    mainChart.getChartArea().repaint();
    refreshButton.setEnabled(false);
  }

  /**
   * Invoked when data has been loaded for both primary and secondary symbols.
   */
  protected void endLoading() {
    if (loadingMessage != null)
      mainChart.removeChartDrawListener(loadingMessage);
    mainChart.getChartArea().repaint();
    refreshButton.setEnabled(true);
  }

  /** Loads the quotes. */
  private void loadQuotes(final String symbol, Date startDate, Date endDate, int frequency) {
    highlighter.unhighlight();
    startLoading();

    // == Reset all data
    overviewRenderer.getDataSource().setAll(null);
    detailedRenderer.getDataSource().setAll(null);
    secondaryRenderer.getDataSource().setAll(null);

    // == Workaround BUG HiLoChartRenderer (setDataSets(null))
    var mode = ((HiLoChartRenderer) detailedRenderer).getMode();
    if (mode == HiLoChartRenderer.Mode.CANDLE)
      setDetailedRepresentation(CANDLE);
    else
      setDetailedRepresentation(HLOC);
    // == End Workaround

    stockDS.reset();
    updateIndicators();
    StockDataSource.LoadHook afterLoad = new StockDataSource.LoadHook() {
      @Override
      public void dataLoaded(StockDataSource stockDS) {
        primaryDataLoaded(symbol);
      }

      @Override
      public void dataLoaded(DoubleArray[] data) {
      }
    };
    stockDS.loadData(symbol, startDate, endDate, frequency, afterLoad);
  }

  private void loadDefaultData() {
    stockDS.loadDefaultData();
    primaryDataLoaded(StockDataSource.DEFAULT_SYMBOL);
    installReferenceIndicators();
  }

  private void primaryDataLoaded(String expectedSymbol) {
    if (stockDS.size() > 0) {
      String symbol = stockDS.getSymbol().toUpperCase();
      if (!symbol.equalsIgnoreCase(expectedSymbol)) {
        String msg = "Primary symbol [" + expectedSymbol + "] not found. Default data loaded";
        JOptionPane.showMessageDialog(topFrame, msg, "Loading Error", JOptionPane.ERROR_MESSAGE);
      }

      // == Update overview Renderer
      overviewRenderer.getDataSource().add(stockDS.getCloseDataSet());
      overviewRenderer.setName(symbol);

      // == Update detailed renderer
      DataSet[] quotes = { stockDS.getHighDataSet(), // high
          stockDS.getLowDataSet(), // low
          stockDS.getOpenDataSet(), // open
          stockDS.getCloseDataSet() // close
      };
      detailedRenderer.getDataSource().setAll(quotes);
      detailedRenderer.setName(symbol);
      Color c = PRIMARY_COLOR;
      PlotStyle style1 = PlotStyle.createStroked(c);
      PlotStyle style2 = new PlotStyle(c, Color.white);
      PlotStyle style3 = new PlotStyle(c, c.darker());
      detailedRenderer.setStyles(new PlotStyle[] { style1, style1, style2, style3 });


      // == Load data for secondary symbols
      final String[] secondarySymbols = queryPanel.getSecondarySymbols();
      if (secondarySymbols != null) {
        percentDisplayer.setActive(false);
        StockDataSource.LoadHook afterLoad = new StockDataSource.LoadHook() {
          @Override
          public void dataLoaded(StockDataSource stockDS) {
          }

          @Override
          public void dataLoaded(DoubleArray[] data) {
            secondaryDataLoaded(secondarySymbols, data);
          }
        };
        stockDS.loadData(secondarySymbols, afterLoad);
      } else {
        percentDisplayer.setActive(false);
        endLoading();
      }
    } else {
      endLoading();
      // Loading can be interrupted,
      // String msg = "Cannot load data.";
      // JOptionPane.showMessageDialog(topFrame, msg, "Loading Error",
      // JOptionPane.ERROR_MESSAGE);
    }

    // checkSorted();
    // == Update indicatore
    updateIndicators();

    // == Update the x-scale and focus the default viewport.
    updateXScale();
    applyReferenceVisibleRange();
    zoomHistory.reset();
    zoomHistory.add(mainChart.getXAxis().getVisibleRange());
    // System.out.println("primaryDataLoaded");
  }

  private void installReferenceIndicators() {
    if (!upperIndicators.isEmpty())
      return;

    addUpperIndicator(new PriceChannelIndicator(stockDS));
    addUpperIndicator(MovingAverageIndicator.createEMA(stockDS, 20));
    addUpperIndicator(MovingAverageIndicator.createEMA(stockDS, 40));
    addUpperIndicator(MovingAverageIndicator.createSMA(stockDS, 80));
    updateIndicators();
  }

  private void applyReferenceVisibleRange() {
    DataSet closeDataSet = stockDS.getCloseDataSet();
    if (closeDataSet == null || closeDataSet.size() == 0)
      return;

    int lastVisibleIndex = closeDataSet.size() - 1;
    int firstVisibleIndex = Math.max(0, lastVisibleIndex - DEFAULT_VISIBLE_BAR_COUNT);
    mainChart.getXAxis().setAutoVisibleRange(false);
    mainChart.getXAxis().setVisibleRange(firstVisibleIndex, lastVisibleIndex);
    overviewRenderer.setVisible(false);
    detailedRenderer.setVisible(true);
    xRangeChanged(false);
  }

  private void secondaryDataLoaded(String[] symbols, DoubleArray[] data) {
    DefaultDataSource dataSource = new DefaultDataSource();
    if (data != null) { // else loading was interrupted by another request
      // Update the renderer for the secondary symbols.
      String error = null;
      for (int i = 0; i < data.length; ++i) {
        if (data[i] == null) {
          if (error == null)
            error = symbols[i];
          else
            error += ", " + symbols[i];
          continue;
        }
        String name = symbols[i].toUpperCase();
        data[i].trim();
        dataSource.add(new DefaultDataSet(name, data[i].data()));
      }
      if (error != null) {
        String msg = "The following secondary symbol(s) were not found: " + error;
        JOptionPane.showMessageDialog(topFrame, msg, "Loading Error", JOptionPane.ERROR_MESSAGE);
      }
    }
    secondaryRenderer.setDataSource(dataSource);
    endLoading();
  }

  /**
   * Invoked when quotes are loaded for the primary symbol. This method updates
   * the steps definition of the x-scale.
   */
  private void updateXScale() {
    Date[] dates = stockDS.getDates();
    if (dates == null) {
      sharedTimeScale.setVisible(false);
      sharedTimeScale.setStepsDefinition(new DefaultStepsDefinition());
    } else {
      TimeUnit unit = null;
      switch (stockDS.getFrequency()) {
      case StockDataSource.MONTHLY:
        unit = TimeUnit.MONTH;
        break;
      case StockDataSource.WEEKLY:
        unit = TimeUnit.WEEK;
        break;
      case StockDataSource.DAILY:
      default:
        unit = TimeUnit.DAY;
        break;
      }
      CategoryTimeSteps def;
      def = new CategoryTimeSteps(dates, unit);
      sharedTimeScale.setStepsDefinition(def);
      sharedTimeScale.setVisible(true);
    }
  }

  /**
   * Invoked when quotes are loaded for the primary symbol. This method updates
   * the indicators and the enabled state of their associated actions
   */
  private void updateIndicators() {
    // Refresh indicators.
    for (int i = 0; i < lowerIndicators.length; ++i) {
      if (lowerIndicators[i] != null)
        lowerIndicators[i].refresh();
    }

    Iterator<TechnicalIndicator> ite = upperIndicators.iterator();
    while (ite.hasNext())
      ite.next().refresh();

    // Update the enabled state of actions.
    Iterator<IndicatorAction> ite2 = upperIndicatorActions.iterator();
    while (ite2.hasNext()) {
      ite2.next().updateEnabled();
    }
  }

  private void resetVisibleRange() {
    mainChart.getXAxis().setAutoVisibleRange(true);
    mainChart.getYAxis(0).setAutoVisibleRange(true);
    zoomHistory.reset();
  }


  private void updateIndicatorChartRanges() {
    if (lowerCharts == null || lowerCharts.length < 2)
      return;

    DataInterval xRange = mainChart.getXAxis().getVisibleRange();
    if (xRange.isEmpty())
      return;

    DataInterval volumeRange = StockUtil.getYDataRange(lowerCharts[0], 0, xRange);
    if (!volumeRange.isEmpty()) {
      double max = Math.max(1.0, volumeRange.getMax());
      lowerCharts[0].getYAxis(0).setVisibleRange(0.0, max);
    }

    lowerCharts[1].getYAxis(0).setVisibleRange(0.0, 100.0);
  }
  /**
   * Creates a legend for the charts.
   */
  protected Legend createLegend() {
    Legend legend = new Legend();
    legend.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 2));
    legend.setPaintingBackground(true);
    legend.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(90, 98, 108, 60)),
        BorderFactory.createEmptyBorder(1, 6, 1, 6)));
    legend.setBackground(LEGEND_BACKGROUND);
    legend.setTransparency(205);
    legend.setOpaque(false);
    legend.setForeground(FOREGROUND);
    legend.setMovable(false);
    legend.setFont(DEFAULT_FONT);
    // TODO
    //legend.setSymbolSize(new Dimension(16, 12));
    return legend;
  }

  private void attachLegend(Chart chart, Legend legend) {
    chart.setLegend(legend);
    chart.addLegend(legend, ChartLayout.ABSOLUTE, false);
    LegendPositioner positioner = new LegendPositioner(chart, legend, LEGEND_MARGIN);
    chart.addChartListener(positioner);
    chart.getChartArea().addComponentListener(positioner);
    legend.addComponentListener(positioner);
    positioner.updateBounds();
  }

  private static final class LegendPositioner extends ComponentAdapter implements ChartListener {
    private final Chart chart;
    private final Legend legend;
    private final int margin;

    LegendPositioner(Chart chart, Legend legend, int margin) {
      this.chart = chart;
      this.legend = legend;
      this.margin = margin;
    }

    @Override
    public void chartAreaChanged(ChartAreaEvent evt) {
      if (evt.getChart() == chart)
        updateBounds();
    }

    @Override
    public void componentMoved(ComponentEvent e) {
      updateBounds();
    }

    @Override
    public void componentResized(ComponentEvent e) {
      updateBounds();
    }

    void updateBounds() {
      if (legend.getParent() != chart)
        return;
      Chart.Area area = chart.getChartArea();
      Rectangle plotRect = area.getPlotRect();
      if (plotRect == null)
        return;
      Point location = area.getLocation();
      Dimension size = legend.getPreferredSize();
      legend.setBounds(location.x + plotRect.x + margin, location.y + plotRect.y + margin, size.width, size.height);
    }
  }

  /**
   * Creates a new <code>Chart</code> and sets common properties.
   */
  protected Chart createChart() {
    Chart chart = new Chart();
    chart.getXGrid().setMajorPaint(GRID_COLOR);
    chart.getYGrid(0).setMajorPaint(GRID_COLOR);
    chart.getXGrid().setDrawOrder(-2);
    chart.getYGrid(0).setDrawOrder(-2);
    chart.setForeground(FOREGROUND);
    chart.setBackground(BACKGROUND);
    chart.setFont(DEFAULT_FONT);
    chart.getChartArea().setBackground(BACKGROUND);
    chart.getChartArea().setBorder(BorderFactory.createEmptyBorder());
    chart.getYScale(0).setVisible(true);
    chart.getYScale(0).setAxisVisible(true);
    chart.getYScale(0).setLabelVisible(true);
    chart.getYScale(0).setMajorTickVisible(true);
    chart.getYScale(0).setLabelFont(new Font("Dialog", Font.PLAIN, 11));
    chart.getXScale().setLabelFont(new Font("Dialog", Font.PLAIN, 11));
    PlotStyle style = chart.getChartArea().getPlotStyle();
    if (style != null)
      chart.getChartArea().setPlotStyle(style.setFillOn(false));
    return chart;
  }

  /**
   * Creates the chart interactors.
   */
  private void createInteractors() {
    // == Upper interactors
    upperInteractors[0] = new ChartInteractor[5];

    // Create the interactor that highlights quotes.
    HighlightQuotesInteractor highlightInter = new HighlightQuotesInteractor() {
      @Override
      protected boolean isTarget(ChartRenderer r) {
        return r == overviewRenderer || r == detailedRenderer || r.getParent() == detailedRenderer;
      }
    };

    // Add a listener to handle highlighting.
    highlighter = new Highlighter();
    highlightInter.addChartInteractionListener(highlighter);
    upperInteractors[0][0] = highlightInter;

    // Add zooming behavior. Subclassed to store the new visible range
    // in the axis zoom history.
    AutoYZoomInteractor autoYZoomInter = new AutoYZoomInteractor() {
      @Override
      protected void doIt() {
        DataInterval oldRange = getXAxis().getVisibleRange();
        super.doIt();
        DataInterval newRange = getXAxis().getVisibleRange();
        if (!oldRange.equals(newRange))
          zoomHistory.add(newRange);
      }

    };
    autoYZoomInter.setAnimationStep(StockUtil.ANIMATION_STEPS);
    upperInteractors[0][1] = autoYZoomInter;

    // Add panning behavior
    upperInteractors[0][2] = new ChartPanInteractor();
    upperInteractors[0][3] = new ChartXScrollInteractor();

    // Add zooming behavior on the scale
    ZoomScaleInteractor zoomScaleInter = new ZoomScaleInteractor(-1) {
      @Override
      protected void zoomScale() {
        super.zoomScale();
        DataInterval itv = getXAxis().getVisibleRange();
        zoomHistory.add(itv);
      }
    };
    upperInteractors[0][4] = zoomScaleInter;

    // <code>localInters</code> stores interactors related to local zoom
    // interactions.
    upperInteractors[1] = new ChartInteractor[3];
    upperInteractors[1][0] = new ChartLocalPanInteractor(0, MouseEvent.BUTTON3_DOWN_MASK);

    upperInteractors[1][1] = new ChartLocalReshapeInteractor() {
      // == Workaround limitation (adjusting state not set on the axis).
      @Override
      protected void startOperation(MouseEvent evt) {
        super.startOperation(evt);
        mainChart.getXAxis().setAdjusting(true);
      }

      @Override
      protected void endOperation(MouseEvent evt) {
        super.endOperation(evt);
        mainChart.getXAxis().setAdjusting(false);
      }

    };
    upperInteractors[1][2] = highlightInter;

    // == Lower interactors
    lowerInteractors[0] = new ChartInteractor[lowerCharts.length][];
    lowerInteractors[1] = new ChartInteractor[lowerCharts.length][];
    for (int i = 0; i < lowerCharts.length; ++i) {
      lowerInteractors[0][i] = new ChartInteractor[] { new ChartPanInteractor() };
      lowerInteractors[1][i] = null;
    }
  }

  /**
   * Invoked when the visible time range changes.
   * <p>
   * This method toggles the visibility of the quote overview/quote renderers
   * according to the visible time range.
   */
  private void xRangeChanged(boolean zoom) {
    DataInterval xRange = mainChart.getXAxis().getVisibleRange();
    if (xRange.isEmpty())
      return;

    DataInterval yRange = StockUtil.getYDataRange(mainChart, 0, xRange);

    if (!yRange.isEmpty()) {
      double padding = Math.max(yRange.getLength() * 0.04, 1.0E-6);
      yRange.expand(padding);
      mainChart.getYAxis(0).setVisibleRange(yRange);
    }

    updateIndicatorChartRanges();

    if (zoom) {
      boolean hideOverview = (xRange.getLength() < OVERVIEW_THRESHOLD);
      mainChart.getYAxis(0).setAutoDataRange(false);
      getDetailedMenu().setEnabled(!hideOverview);
      getOverviewMenu().setEnabled(hideOverview);
      overviewRenderer.setVisible(!hideOverview);
      detailedRenderer.setVisible(hideOverview);
      mainChart.getYAxis(0).setAutoDataRange(true);
      LocalZoomHandler lzh = LocalZoomHandler.get(mainChart, -1);
      if (lzh != null) {
        double middle = xRange.getMiddle();
        double length = xRange.getLength();
        lzh.getTransformer().setZoomRange(middle - length / 30, middle + length / 30);
      }
    }
  }

  /**
   * Specifies the stock overview representation.
   * 
   * @param representation
   *          The desired representation.
   * @see #BAR
   * @see #LINE
   * @see #AREA
   * @see #STAIR
   */
  public void setOverviewRepresentation(int representation) {
    int idx = mainChart.getRenderers().indexOf(overviewRenderer);
    ChartRenderer r = null;
    Color c = DEFAULT_COLORS[0];
    // Stroke stroke = new BasicStroke(2.f, BasicStroke.CAP_ROUND,
    // BasicStroke.JOIN_ROUND);
    PlotStyle filledStyle = new PlotStyle(c, ColorEx.setAlpha(c, .45f));
    PlotStyle strokedStyle = new PlotStyle(2.f, c);
    switch (representation) {
    case BAR:
      r = new SingleBarRenderer(filledStyle, 100.);
      break;
    case STAIR:
      r = new SingleStairRenderer(filledStyle);
      break;
    case AREA:
      r = new SingleAreaRenderer(filledStyle);
      break;
    case LINE:
    default:
      r = new SinglePolylineRenderer(strokedStyle);
      break;
    }
    if (idx == -1) {
      mainChart.addRenderer(r);
    } else {
      r.setVisible(overviewRenderer.isVisible());
      r.setName(overviewRenderer.getName());
      mainChart.setRenderer(idx, r);
      // Update y-range explicitly since the setRenderer can modify
      // the x-range before the new renderer is added.
      xRangeChanged(false);
    }
    overviewRenderer = r;
  }

  /**
   * Specifies the stock overview representation.
   * 
   * @param representation
   *          The desired representation.
   * @see #HLOC
   * @see #CANDLE
   */
  public void setDetailedRepresentation(int representation) {
    int idx = mainChart.getRenderers().indexOf(detailedRenderer);
    ChartRenderer r = null;
    switch (representation) {
    case HLOC:
      r = new HiLoChartRenderer(HiLoChartRenderer.Mode.OPENCLOSE, SingleHiLoRenderer.Type.STICK, 100);
      break;
    case CANDLE:
    default:
      r = new HiLoChartRenderer(HiLoChartRenderer.Mode.CANDLE, SingleHiLoRenderer.Type.STICK, CANDLE_WIDTH_PERCENT);
      break;
    }
    if (idx == -1)
      mainChart.addRenderer(r);
    else {
      r.setVisible(detailedRenderer.isVisible());
      r.setName(detailedRenderer.getName());
      PlotStyle[] styles = detailedRenderer.getStyles();
      mainChart.setRenderer(idx, r);
      r.setStyles(styles);
      xRangeChanged(false);
    }
    detailedRenderer = r;
  }

  /**
   * Returns the default start date for quote queries.
   * 
   * @return One year before the current date.
   */
  static Date getDefaultStartDate() {
    Calendar cal = Calendar.getInstance(Locale.getDefault());
    cal.setTime(new Date());
    cal.add(Calendar.YEAR, -1);
    return cal.getTime();
  }

  /** Creates a label with a default font and foreground */
  private static JLabel createLabel(String text) {
    JLabel label = new JLabel(text);
    label.setForeground(FOREGROUND);
    label.setFont(DEFAULT_FONT);
    return label;
  }

  /** Returns the highlight label for the specified chart. */
  private static JLabel getHighlightStatus(Chart chart) {
    return (JLabel) chart.getClientProperty(HIGHLIGHT_STATUS_KEY);
  }

  /** Creates a toggle button for the toolbar */
  private JToggleButton createToggleButton(String iconName, String selIconName, Dimension size, String actionCommand,
      String actionDesc) {
    Icon icon = StockUtil.loadIcon(iconName);
    JToggleButton toggle = new JToggleButton(icon);
    icon = StockUtil.loadIcon(selIconName);
    toggle.setSelectedIcon(icon);
    toggle.setPreferredSize(size);
    toggle.setMaximumSize(size);
    toggle.setMinimumSize(size);
    toggle.setActionCommand(actionCommand);
    toggle.setToolTipText(actionDesc);
    toggle.addActionListener(this);
    return toggle;
  }

  /**
   * Adds the specified action to a toolbar and returns the created
   * <code>JButton</code>.
   */
  @Override
  protected JButton addAction(JToolBar toolbar, ChartAction action) {
    JButton button = super.addAction(toolbar, action);
    button.setPreferredSize(new Dimension(28, 28));
    return button;
  }

  /**
   * Application mainline.
   */
  public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        JFrame topFrame = new JFrame("Perforce JViews Charts Stock Demo");
        StockDemo demo = new StockDemo();
        demo.init(topFrame.getContentPane());
        demo.setFrameGeometry(MAIN_WINDOW_DEFAULT_WIDTH, MAIN_WINDOW_DEFAULT_HEIGHT, true);
        topFrame.setVisible(true);
      }
    });
  }

  // --------------------------------------------------------------------------
  /**
   * The base action class for setting indicators.
   */
  abstract class IndicatorAction extends AbstractAction {
    private final TechnicalIndicator indicator;

    /**
     * Creates a new <code>UpperIndicatorAction</code> that references the
     * specified indicator.
     */
    public IndicatorAction(TechnicalIndicator indicator) {
      super(indicator.getName());
      this.indicator = indicator;
    }

    /**
     * Computes the enable state of the action.
     */
    public void updateEnabled() {
      boolean enabled = (stockDS.size() > 0);
      setEnabled(enabled);
    }

    /**
     * Returns the indicator associated with this action.
     */
    public final TechnicalIndicator getIndicator() {
      return indicator;
    }
  }

  /**
   * An action class used to add upper indicators.
   */
  class UpperIndicatorAction extends IndicatorAction {
    private boolean added;

    /**
     * Creates a new <code>UpperIndicatorAction</code> that references the
     * specified indicator.
     */
    public UpperIndicatorAction(TechnicalIndicator indicator) {
      super(indicator);
    }

    /** Invoked when an action occurs. */
    @Override
    public void actionPerformed(ActionEvent evt) {
      if (!isEnabled())
        return;
      if (added) {
        removeUpperIndicator(getIndicator());
        added = false;
      } else {
        addUpperIndicator(getIndicator());
        added = true;
      }
    }
  }

  /**
   * An action class used to set lower indicators.
   */
  class LowerIndicatorAction extends IndicatorAction {
    private final int chartIdx;

    /**
     * Creates a new <code>LowerIndicatorAction</code> that references the
     * specified indicator.
     */
    public LowerIndicatorAction(TechnicalIndicator indicator, int chartIdx) {
      super(indicator);
      this.chartIdx = chartIdx;
    }

    /** Invoked when an action occurs. */
    @Override
    public void actionPerformed(ActionEvent evt) {
      if (!isEnabled())
        return;
      setLowerIndicator(chartIdx, getIndicator());
    }
  }

  // --------------------------------------------------------------------------
  /**
   * Listens to the events sent by the highlight interactor and performs the
   * highlighting operations.
   */
  private class Highlighter implements ChartInteractionListener {
    private final HighlightIndicator upperHighlight;
    private final HighlightIndicator[] lowerHighlights;

    public Highlighter() {
      upperHighlight = createHighlightIndicator();
      mainChart.addDecoration(upperHighlight);

      int count = lowerCharts.length;
      lowerHighlights = new HighlightIndicator[count];
      for (int i = 0; i < count; ++i) {
        lowerHighlights[i] = createHighlightIndicator();
        lowerCharts[i].addDecoration(lowerHighlights[i]);
      }
    }

    @Override
    public void interactionPerformed(ChartInteractionEvent evt) {
      ChartHighlightInteractionEvent hevt = (ChartHighlightInteractionEvent) evt;
      if (hevt.isHighlighted())
        highlight(hevt.getDisplayPoint());
      else
        unhighlight();
    }

    private void highlight(DisplayPoint hdp) {
      if (detailedRenderer.isVisible()) {
        hdp = detailedRenderer.getDisplayPoint(stockDS.getCloseDataSet(), hdp.getIndex());
        if (hdp == null)
          return;
      }

      upperHighlight.setHighlightedPoint(hdp);
      for (int i = 0; i < lowerHighlights.length; ++i) {
        DisplayPoint idp = lowerIndicators[i].getHighlightedPoint(hdp.getIndex());
        lowerHighlights[i].setHighlightedPoint(idp);
        if (idp == null) {
          lowerHighlights[i].setValue(hdp.getXData());
          if (!lowerHighlights[i].isVisible())
            lowerHighlights[i].setVisible(true);
        } else {
          lowerHighlights[i].setVisible(true);
          JLabel status = getHighlightStatus(lowerCharts[i]);

          if (status != null && idp != null) {
            status.setText(numFmt.format(idp.getYData()));
          }
        }
      }
      quoteDisplay.displayQuote(hdp);
    }

    private void unhighlight() {
      upperHighlight.setHighlightedPoint(null);
      for (int i = 0; i < lowerHighlights.length; ++i) {
        lowerHighlights[i].setHighlightedPoint(null);
        if (lowerHighlights[i].isVisible())
          lowerHighlights[i].setVisible(false);
        JLabel status = getHighlightStatus(lowerCharts[i]);
        if (status != null)
          status.setText("");
      }
      quoteDisplay.displayQuote(null);
    }

    private HighlightIndicator createHighlightIndicator() {
      HighlightIndicator indic = new HighlightIndicator(-1);
      indic.setDrawOrder(2);
      indic.setColor(FOREGROUND);
      indic.setVisible(false);
      return indic;
    }
  }

  /** A panel displaying highlighed quote values. */
  private class QuoteDisplayPanel extends JPanel {
    private final int WIDTH = 62;
    private final int SPACE = 6;
    JLabel date, high, low, open, close;

    public QuoteDisplayPanel() {
      super(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(2, 12, 2, 4));
      Box box = Box.createHorizontalBox();
      setOpaque(false);
      box.add(createLabel("DATE"));
      box.add(Box.createHorizontalStrut(SPACE));
      date = new FixedLabel(WIDTH, JLabel.RIGHT);
      box.add(date);
      box.add(Box.createHorizontalGlue());
      box.add(createLabel("OPEN"));
      box.add(Box.createHorizontalStrut(SPACE));
      open = new FixedLabel(WIDTH, JLabel.RIGHT);
      box.add(open);
      box.add(Box.createHorizontalGlue());
      box.add(createLabel("HIGH"));
      box.add(Box.createHorizontalStrut(SPACE));
      high = new FixedLabel(WIDTH, JLabel.RIGHT);
      box.add(high);
      box.add(Box.createHorizontalGlue());
      box.add(createLabel("LOW"));
      box.add(Box.createHorizontalStrut(SPACE));
      low = new FixedLabel(WIDTH, JLabel.RIGHT);
      box.add(low);
      box.add(Box.createHorizontalGlue());
      box.add(createLabel("CLOSE"));
      box.add(Box.createHorizontalStrut(SPACE));
      close = new FixedLabel(WIDTH, JLabel.RIGHT);
      box.add(close);
      box.add(Box.createHorizontalGlue());
      add(box);
    }

    public void displayQuote(DisplayPoint dp) {
      if (stockDS.size() < 4 || dp == null) {
        date.setText(" ");
        open.setText(" ");
        high.setText(" ");
        low.setText(" ");
        close.setText(" ");
      } else {
        int idx = dp.getIndex();
        date.setText(dateFmt.format(stockDS.getDate(idx)));
        double val = stockDS.getOpenDataSet().getYData(idx);
        open.setText(numFmt.format(val));
        val = stockDS.getHighDataSet().getYData(idx);
        high.setText(numFmt.format(val));
        val = stockDS.getLowDataSet().getYData(idx);
        low.setText(numFmt.format(val));
        val = stockDS.getCloseDataSet().getYData(idx);
        close.setText(numFmt.format(val));
      }
    }
  }

}














