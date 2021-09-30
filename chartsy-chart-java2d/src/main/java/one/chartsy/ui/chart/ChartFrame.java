package one.chartsy.ui.chart;

import one.chartsy.ui.chart.components.ChartToolbar;
import one.chartsy.ui.chart.components.MainPanel;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

public class ChartFrame extends JPanel {
    /** The top JLayer wrapped around the entire chart frame area. */
    private final JLayer<JPanel> chartLayer = new JLayer<>();

    private ChartToolbar chartToolbar;
    private MainPanel mainPanel;


    public ChartFrame() {
        super(new BorderLayout());
        setOpaque(false);
        add(chartLayer);
    }

    @Override
    public void addNotify() {
        initComponents();
        super.addNotify();
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
