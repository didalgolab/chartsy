package one.chartsy.ui.chart;

import one.chartsy.SymbolIdentity;
import one.chartsy.TimeFrame;
import one.chartsy.commons.event.ListenerList;
import one.chartsy.data.CandleSeries;
import one.chartsy.ui.chart.components.ChartToolbar;
import one.chartsy.ui.chart.components.MainPanel;
import one.chartsy.ui.chart.internal.ChartFrameDropTarget;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openide.util.NbBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.lang.reflect.InvocationTargetException;

public class ChartFrame extends JPanel implements ChartContext, MouseWheelListener {

    private final Logger log = LogManager.getLogger(getClass());
    private final JLayer<JPanel> chartLayer = new JLayer<>();

    private ChartToolbar chartToolbar;
    private MainPanel mainPanel;
    private JScrollBar scrollBar;

    private ChartProperties chartProperties = new ChartProperties();
    private ChartData chartData;
    private ChartHistory history = new ChartHistory();
    private Template template;

    private final transient ListenerList<ChartFrameListener> chartFrameListeners = ListenerList.of(ChartFrameListener.class);

    public ChartFrame() {
        super(new BorderLayout());
        setOpaque(false);
        add(chartLayer);
    }

    protected void initComponents() {
        chartToolbar = new ChartToolbar(this);
        chartToolbar = new ChartToolbar(this);
        mainPanel = new MainPanel(this);
        ChartFrameDropTarget.decorate(this);
        scrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
        scrollBar.setAlignmentX(java.awt.Component.RIGHT_ALIGNMENT);

        JPanel chartLayerView = new JPanel(new BorderLayout());
        chartLayerView.add(chartToolbar, BorderLayout.NORTH);
        chartLayerView.add(new JLayer<>(mainPanel, new XORCrosshairActiveRendererLayer()), BorderLayout.CENTER);
        //		chartLayerView.add(mainPanel, BorderLayout.CENTER);
        chartLayerView.add(scrollBar, BorderLayout.SOUTH);
        chartLayer.setView(chartLayerView);

        // add chart JLayer as a direct child of this frame
        add(chartLayer);

        validate();

        if (template != null) {
            for (Overlay overlay : template.getOverlays())
                fireOverlayAdded(overlay);
            for (Indicator indicator : template.getIndicators())
                fireIndicatorAdded(indicator);
        }
        if (chartData.getAnnotations() != null)
            restoreAnnotations();

        addMouseWheelListener(this);
        scrollBar.getModel().addChangeListener(e -> {
            ChartData chartData = getChartData();
            int items = chartData.getPeriod();
            int itemsCount = chartData.getDatasetLength();
            int end = scrollBar.getModel().getValue() + items;

            end = end > itemsCount ? itemsCount : (end < items ? items : end);

            if (chartData.getLast() != end) {
                chartData.setLast(end);
                chartData.calculate(ChartFrame.this);
            }
            repaint();
        });
    }

    public Logger log() {
        return log;
    }

    @Override
    public boolean getValueIsAdjusting() {
        return scrollBar.getValueIsAdjusting();
    }

    @Override
    public JPopupMenu getMenu() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void indicatorRemoved(Indicator indicator) {
        removeChartFrameListener(indicator);
        chartFrameListeners.fire().indicatorRemoved(indicator);
    }

    @Override
    public void fireOverlayRemoved(Overlay overlay) {
        removeChartFrameListener(overlay);
        chartFrameListeners.fire().overlayRemoved(overlay);
    }

    @Override
    public void zoomIn() {
        double barWidth = chartProperties.getBarWidth();
        double newWidth = chartData.zoomIn(barWidth);
        if (barWidth != newWidth) {
            chartProperties.setBarWidth(newWidth);
            repaint();
        }
    }

    @Override
    public void zoomOut() {
        double barWidth = chartProperties.getBarWidth();
        double newWidth = chartData.zoomOut(barWidth);
        if (barWidth != newWidth) {
            chartProperties.setBarWidth(newWidth);
            repaint();
        }
    }

    @Override
    public Template getTemplate() {
        return template;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (getChartData().hasDataset()) {
            int items = getChartData().getPeriod();
            int itemsCount = getChartData().getDatasetLength();
            if (itemsCount > items) {
                int last = getChartData().getLast() - e.getWheelRotation();
                last = last > itemsCount ? itemsCount : (last < items ? items : last);

                if (getChartData().getLast() != last) {
                    getChartData().setLast(last);
                    getChartData().calculate(this);
                }
            }
        }
    }

    @Override
    public void addNotify() {
        initComponents();
        super.addNotify();
    }

    private void restoreAnnotations() {

    }

    public void fireOverlayAdded(Overlay overlay) {
        addChartFrameListener(overlay);
        CandleSeries dataset = chartData.getDataset();
        if (dataset != null) {
            overlay.setDataset(dataset);
            overlay.calculate(); // TODO: make the calc asynchronous
        }
        // notify listeners
        chartFrameListeners.fire().overlayAdded(overlay);
    }

    public void fireIndicatorAdded(Indicator indicator) {
        addChartFrameListener(indicator);
        CandleSeries dataset = chartData.getDataset();
        if (dataset != null) {
            indicator.setDataset(dataset);
            indicator.calculate(); // TODO: make asynchronous
        }
        chartFrameListeners.fire().indicatorAdded(indicator);
    }

    @Override
    public MainPanel getMainPanel() {
        return mainPanel;
    }

    public ChartHistory getHistory() {
        return this.history;
    }

    public void setHistory(ChartHistory history) {
        this.history = history;
    }

    @Override
    public ChartProperties getChartProperties() {
        return chartProperties;
    }

    @Override
    public ChartData getChartData() {
        return chartData;
    }


    /**
     * Changes {@code ChartData} associated with this chart frame.
     *
     * @param data
     *            the chart data
     * @throws IllegalArgumentException
     *             if the {@code data} or {@code data.getSymbol()} is
     *             {@code null}
     */
    public void setChartData(ChartData data) {
        if (data == null)
            throw new IllegalArgumentException("ChartData cannot be NULL");
        if (data.getSymbol() == null)
            throw new IllegalArgumentException("ChartData Symbol cannot be NULL");
        if (chartData != null)
            removeChartFrameListener(chartData);

        chartData = data;
        addChartFrameListener(data);
        setName(NbBundle.getMessage(ChartFrame.class, "ChartFrame.name", chartData.getSymbol().name()));
    }

    @Override
    public void addChartFrameListener(ChartFrameListener listener) {
        chartFrameListeners.addListener(listener);
    }

    public void removeChartFrameListener(ChartFrameListener listener) {
        chartFrameListeners.removeListener(listener);
    }

    @Override
    public void updateHorizontalScrollBar() {
        int last = getChartData().getLast();
        int items = getChartData().getPeriod();
        int itemsCount = getChartData().getDatasetLength();

        scrollBar.setValues(last - items, items, 0, itemsCount);
        scrollBar.setBlockIncrement(Math.max(1, items - 1));
    }

    /**
     * Performs the given chart action, which usually results in a change of a
     * {@code Symbol} or a {@code TimeFrame} currently opened by this chart
     * frame. All associated {@code ChartFrameListener}'s are notified about the
     * change by calling theirs {@link ChartFrameListener#symbolChanged(SymbolIdentity)}
     * method.
     *
     * @param action
     *            the {@code ChartAction} to perform on this {@code ChartFrame}
     */
    public void navigationChange(ChartHistoryEntry action) {
        SymbolIdentity symbol = action.getSymbol();
        TimeFrame timeFrame = action.getTimeFrame();

        datasetLoading(symbol, timeFrame);

        // reverse back to predefined bar width
        if (template != null)
            chartProperties.setBarWidth(template.getChartProperties().getBarWidth());

        // notify listeners
        chartFrameListeners.fire().symbolChanged(symbol);
    }

    /**
     * Changes the chart frame symbol currently displayed. All associated
     * {@code ChartFrameListener}'s are notified about the change by calling
     * theirs {@link ChartFrameListener#symbolChanged(SymbolIdentity)} method.
     *
     * @param newSymbol
     *            the new symbol to set
     */
    public void symbolChanged(SymbolIdentity newSymbol) {
        TimeFrame timeFrame = chartData.getTimeFrame();
        datasetLoading(newSymbol, timeFrame);

        // notify listeners
        chartFrameListeners.fire().symbolChanged(newSymbol);
    }

    private void datasetLoading(SymbolIdentity newSymbol, TimeFrame timeFrame) {
        throw new UnsupportedOperationException();
    }

    protected static void createAndShowGUI() {
        var frame = new JFrame("Trading Chart");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().add(new ChartFrame());
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(ChartFrame::createAndShowGUI);
    }
}
