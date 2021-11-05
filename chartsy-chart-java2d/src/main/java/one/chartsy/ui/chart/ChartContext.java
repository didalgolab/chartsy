package one.chartsy.ui.chart;

import one.chartsy.ui.chart.components.MainPanel;
import org.apache.logging.log4j.Logger;

import javax.swing.JPopupMenu;

/**
 * Represents the chart context.
 * 
 * @author Mariusz Bernacki
 *
 */
public interface ChartContext {

    Logger log();

    ChartProperties getChartProperties();
    
    ChartData getChartData();
    
    MainPanel getMainPanel();
    
    void updateHorizontalScrollBar();
    
    void addChartFrameListener(ChartFrameListener listener);
    
    boolean getValueIsAdjusting();
    
    JPopupMenu getMenu();
    
    void indicatorRemoved(Indicator indicator);
    
    void fireOverlayRemoved(Overlay overlay);
    
    void zoomOut();
    
    void zoomIn();
    
    ChartTemplate getChartTemplate();
    
}
