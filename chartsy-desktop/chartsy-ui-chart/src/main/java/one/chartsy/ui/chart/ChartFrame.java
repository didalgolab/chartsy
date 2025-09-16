/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import lombok.Getter;
import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.core.event.ListenerList;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.Series;
import one.chartsy.ui.chart.components.AnnotationPanel;
import one.chartsy.ui.chart.components.ChartStackPanel;
import one.chartsy.ui.chart.components.ChartToolbar;
import one.chartsy.ui.chart.components.IndicatorPanel;
import one.chartsy.ui.chart.components.MainPanel;
import one.chartsy.ui.chart.data.SymbolResourceLoaderTask;
import one.chartsy.ui.chart.internal.ChartFrameDropTarget;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

import javax.swing.*;
import javax.swing.plaf.LayerUI;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

public class ChartFrame extends JPanel implements ChartContext, MouseWheelListener {

    private final transient Logger log = LogManager.getLogger(getClass());
    private final JLayer<JPanel> chartLayer = new JLayer<>();

    @Getter
    private ChartToolbar chartToolbar;
    private MainPanel mainPanel;
    private JScrollBar scrollBar;

    private ChartProperties chartProperties = new ChartProperties();
    private ChartData chartData;
    private ChartHistory history = new ChartHistory();
    private ChartTemplate chartTemplate;

    private final transient ListenerList<ChartFrameListener> chartFrameListeners = ListenerList.of(ChartFrameListener.class);

    public ChartFrame() {
        super(new BorderLayout());
        setOpaque(false);
        add(chartLayer);
    }

    protected void initComponents(boolean force) {
        if (chartLayer.getView() == null || force) {
            chartToolbar = new ChartToolbar(this);
            mainPanel = new MainPanel(this);
            ChartFrameDropTarget.decorate(this);
            scrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
            scrollBar.setAlignmentX(java.awt.Component.RIGHT_ALIGNMENT);

            JPanel chartLayerView = new JPanel(new BorderLayout());
            chartLayerView.add(chartToolbar, BorderLayout.NORTH);
            JLayer<JComponent> crosshairLayer = new JLayer<>(mainPanel, new StandardCrosshairRendererLayer());
            crosshairLayer.setForeground(Color.lightGray.darker());
            chartLayerView.add(crosshairLayer, BorderLayout.CENTER);
            //		chartLayerView.add(mainPanel, BorderLayout.CENTER);
            chartLayerView.add(scrollBar, BorderLayout.SOUTH);
            chartLayer.setView(chartLayerView);

            // add chart JLayer as a direct child of this frame
            add(chartLayer);

            validate();

            if (chartTemplate != null) {
                for (Overlay overlay : chartTemplate.getOverlays())
                    fireOverlayAdded(overlay);
                for (Indicator indicator : chartTemplate.getIndicators())
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
    }

    @Override
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
    public ChartTemplate getChartTemplate() {
        return chartTemplate;
    }

    public ChartStackPanel getMainStackPanel() {
        if (mainPanel != null) {
            return mainPanel.getStackPanel();
        }
        return null;
    }

    public void setChartTemplate(ChartTemplate chartTemplate) {
        this.chartTemplate = chartTemplate;
        chartProperties.copyFrom(chartTemplate.getChartProperties());

        if (isDisplayable()) {
            for (Overlay overlay : getMainStackPanel().getChartPanel().getOverlays())
                fireOverlayRemoved(overlay);
            for (Overlay overlay : chartTemplate.getOverlays())
                fireOverlayAdded(overlay);

            for (Indicator indicator : getMainStackPanel().getIndicatorsList())
                indicatorRemoved(indicator);
            for (Indicator indicator : chartTemplate.getIndicators())
                fireIndicatorAdded(indicator);
        } else {
            // if the chart is not yet displayed use default bar width from the template
            chartProperties.setBarWidth(chartTemplate.getChartProperties().getBarWidth());
        }
    }

    public void setIndicators(List<Indicator> newIndicators) {
        Indicator[] current = getMainStackPanel().getIndicators();
        for (Indicator i : current)
            indicatorRemoved(i);

        for (Indicator i : newIndicators)
            fireIndicatorAdded(i);
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
        ChartData chartData = getChartData();
        if (chartData.getDataset() == null)
            datasetLoading(chartData.getSymbol(), chartData.getTimeFrame());

        initComponents(false);
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

    public boolean hasCurrentAnnotation() {
        for (var annotationPanel : getAnnotationPanels())
            if (annotationPanel.getSelectionCount() > 0)
                return true;

        return false;
    }

    public Annotation getCurrentAnnotation() {
        for (var annotationPanel : getAnnotationPanels())
            if (annotationPanel.getSelectionCount() > 0)
                return annotationPanel.getSelectedGraphics().iterator().next();

        return null;
    }

    /**
     * Returns the collection of annotation panels currently opened.
     *
     * @return all currently opened annotation panels
     */
    public List<AnnotationPanel> getAnnotationPanels() {
        List<IndicatorPanel> indicatorPanels = getMainStackPanel().getIndicatorPanels();

        List<AnnotationPanel> panels = new ArrayList<>(1 + indicatorPanels.size());
        panels.add(getMainStackPanel().getChartPanel().getAnnotationPanel());
        for (IndicatorPanel indicatorPanel : indicatorPanels)
            panels.add(indicatorPanel.getAnnotationPanel());

        return panels;
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

        if (!timeFrame.equals(chartData.getTimeFrame()))
            chartFrameListeners.fire().timeFrameChanged(timeFrame);

        // reverse back to predefined bar width
        if (chartTemplate != null)
            chartProperties.setBarWidth(chartTemplate.getChartProperties().getBarWidth());

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

    public void timeFrameChanged(TimeFrame newTimeFrame) {
        SymbolIdentity symbol = chartData.getSymbol();
        datasetLoading(symbol, newTimeFrame);

        chartFrameListeners.fire().timeFrameChanged(newTimeFrame);
    }

    private void datasetLoading(SymbolIdentity newSymbol, TimeFrame timeFrame) {
        datasetLoading(SymbolResource.of(newSymbol, timeFrame));
    }

    private volatile ChartHistoryEntry previousChart;
    private final AtomicReference<CompletableFuture<Series<Candle>>> activeLoader = new AtomicReference<>();

    protected void datasetLoading(SymbolResource<Candle> resource) {
        // start a parallel data loading task, as soon as possible
        var provider = getChartData().getDataProvider();
        var task = new SymbolResourceLoaderTask<>(provider, resource);
        activeLoader.set(task);
        ForkJoinPool.commonPool().execute(task);

        // remember currently opened Symbol, TimeFrame and Quotes
        if (chartData.hasDataset()) {
            CandleSeries oldQuotes = chartData.getDataset();
            previousChart = new ChartHistoryEntry(resource.symbol(), resource.timeFrame());
            //previousData = new SoftReference<>(oldQuotes);
        }
        history.actionPerformed(new ChartHistoryEntry(resource.symbol(), resource.timeFrame()));

        // enable the wait layer on the chart frame
        JLabel loadingLabel = createDatasetLoadingLabel(resource.symbol());
        chartLayer.setUI(new WaitLayerUI(loadingLabel));

        // start a progress indicator
        @SuppressWarnings("java:S2095")
        ProgressHandle handle = ProgressHandle.createHandle(loadingLabel.getText());
        handle.start();
        handle.switchToIndeterminate();

        task.whenCompleteAsync((quotes, exception) -> {
            handle.finish();
            if (activeLoader.get() == task) {
                if (quotes != null) {
                    datasetLoaded(quotes);
                } else {
                    datasetLoadingFailed(resource.symbol(), exception);
                }
                revalidate();
                repaint();
            }
        }, /*using Executor:*/SwingUtilities::invokeLater);
    }

    static class WaitLayerUI extends LayerUI<JComponent> {
        private final JLabel label;

        public WaitLayerUI(JLabel label) {
            this.label = label;
        }

        @Override
        public void applyPropertyChange(PropertyChangeEvent event, JLayer<? extends JComponent> layer) {
            if ("datasetLoading.text".equals(event.getPropertyName()))
                label.setText((String) event.getNewValue());
        }

        @Override
        public void eventDispatched(AWTEvent e, JLayer<? extends JComponent> l) {
            if (e instanceof InputEvent)
                ((InputEvent) e).consume();
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);

            @SuppressWarnings("unchecked")
            JLayer<JComponent> layer = (JLayer<JComponent>) c;
            layer.setLayerEventMask(~0);
            if (!(layer.getGlassPane().getLayout() instanceof BorderLayout))
                layer.getGlassPane().setLayout(new BorderLayout());
            layer.getGlassPane().add(label);
            layer.getGlassPane().setVisible(true);
        }

        @Override
        public void uninstallUI(JComponent c) {
            super.uninstallUI(c);

            @SuppressWarnings("unchecked")
            JLayer<JComponent> layer = (JLayer<JComponent>) c;
            layer.setLayerEventMask(0);
            layer.getGlassPane().setVisible(false);
            layer.getGlassPane().removeAll();
        }
    }

    private JLabel createDatasetLoadingLabel(SymbolIdentity symbol) {
        String text = NbBundle.getMessage(ChartFrame.class, "CF.loading", symbol.name());
        // TODO
        Icon icon = null;//ResourcesUtils.getDataManagementIcon();

        @SuppressWarnings("serial")
        JLabel label = new JLabel(text, icon, SwingConstants.CENTER) {

            @Override
            public void paint(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .6f));
                g2.setPaint(Color.WHITE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paint(g);
            }
        };
        label.setHorizontalTextPosition(SwingConstants.CENTER);
        label.setVerticalTextPosition(SwingConstants.BOTTOM);
        label.setForeground(new Color(0x933EC5));
        label.setBackground(Color.WHITE);
        label.setFont(new Font("Aller", Font.BOLD, 17));
        return label;
    }

    /**
     * Called when the data for this chart has been successfully loaded.
     *
     * @param quotes
     *            the quotes for the chart
     */
    protected void datasetLoaded(Series<Candle> quotes) {
        SymbolIdentity symbol = quotes.getResource().symbol();
        try {
            TimeFrame timeFrame = quotes.getResource().timeFrame();
            boolean symbolChanged = !symbol.equals(chartData.getSymbol());

            // notify listeners about newly loaded dataset
            chartFrameListeners.fire().datasetChanged(CandleSeries.from(quotes));
            //remove(loading);
            boolean firstLaunch = (mainPanel == null);
            if (firstLaunch)
                initComponents(false);
            else {
                setName(NbBundle.getMessage(ChartFrame.class, "ChartFrame.name", symbol.name()));
                resetHorizontalScrollBar();
                chartToolbar.updateToolbar();
            }
            chartLayer.setUI(new LayerUI<>());
            if (firstLaunch || previousChart == null || !symbol.equals(previousChart.getSymbol()))
                fireOnChart();

        } catch (Exception e) {
            datasetLoadingFailed(symbol, e);
        }
    }

    protected void fireOnChart() {
        ChartCallbackRegistry callbacks = chartData.getChartCallbacks();
        if (callbacks != null)
            callbacks.fireOnChart(this);
    }

    void datasetLoadingFailed(SymbolIdentity symbol, Throwable x) {
        Exceptions.printStackTrace(x);

        String name = symbol.name();
        String text = NbBundle.getMessage(ChartFrame.class, "datasetLoadingFailed", name);
        chartLayer.propertyChange(new PropertyChangeEvent(this, "datasetLoading.text", null, text));

        if (previousChart != null)
            datasetLoadingFailedQ(symbol);
    }

    private void datasetLoadingFailedQ(SymbolIdentity symbol) {
        NotifyDescriptor descriptor = new NotifyDescriptor.Confirmation("");
        descriptor.setTitle("No Data");
        descriptor.setMessage(NbBundle.getMessage(ChartFrame.class, "CF.loadingFailedQ", symbol.name()));
        descriptor.setOptionType(NotifyDescriptor.YES_NO_OPTION);

        Object result = DialogDisplayer.getDefault().notify(descriptor);
        if (result.equals(NotifyDescriptor.YES_OPTION)) {

            SymbolIdentity oldSymbol = previousChart.getSymbol();
            if (!oldSymbol.equals(chartData.getSymbol())) {
                chartData.setSymbol(oldSymbol);
                chartFrameListeners.fire().symbolChanged(oldSymbol);
            }

            TimeFrame oldTimeFrame = previousChart.getTimeFrame();
            if (!oldTimeFrame.equals(chartData.getTimeFrame())) {
                chartData.setTimeFrame(oldTimeFrame);
                chartFrameListeners.fire().timeFrameChanged(oldTimeFrame);
            }

            resetHorizontalScrollBar();
            chartLayer.setUI(new LayerUI<>());
        }
    }

    public void resetHorizontalScrollBar() {
        chartData.setPeriod(-1);
        chartData.setLast(-1);
        chartData.calculate(this);
        int last = getChartData().getLast();
        int items = getChartData().getPeriod();
        scrollBar.setValues(last - items, items, 0, last);
        scrollBar.setBlockIncrement(Math.max(1, items - 1));
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
