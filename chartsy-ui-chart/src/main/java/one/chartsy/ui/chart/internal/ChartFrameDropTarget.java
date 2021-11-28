package one.chartsy.ui.chart.internal;

import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

import one.chartsy.Symbol;
import one.chartsy.ui.chart.ChartFrame;

public class ChartFrameDropTarget extends DropTargetAdapter {
    /** The chart frame to which this drop target is attached. */
    private final ChartFrame chartFrame;
    
    
    private ChartFrameDropTarget(ChartFrame chartFrame) {
        this.chartFrame = chartFrame;
    }
    
    public static ChartFrameDropTarget decorate(ChartFrame chartFrame) {
        ChartFrameDropTarget listener = new ChartFrameDropTarget(chartFrame);
        new DropTarget(chartFrame, DnDConstants.ACTION_COPY, listener, true, null);
        return listener;
    }
    
    @Override
    public void drop(DropTargetDropEvent event) {
        try {
            Transferable trans = event.getTransferable();
            
            if (event.isDataFlavorSupported(SymbolDataFlavor.dataFlavor)) {
                Symbol currSymbol = (Symbol) trans.getTransferData(SymbolDataFlavor.dataFlavor);
                
                chartFrame.symbolChanged(currSymbol);
                event.acceptDrop(DnDConstants.ACTION_COPY);
                event.dropComplete(true);
            } else
                event.rejectDrop();
        } catch (Exception e) {
            event.rejectDrop();
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Cannot complete drop on " + chartFrame.getClass().getSimpleName() + " due to " + e, e);
        }
    }
}
