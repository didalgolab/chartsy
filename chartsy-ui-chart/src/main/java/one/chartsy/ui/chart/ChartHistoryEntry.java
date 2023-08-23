/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import java.awt.Container;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import one.chartsy.SymbolIdentity;
import one.chartsy.TimeFrame;

/**
 * Represents an element on the {@link ChartHistory}.
 *
 * @author Mariusz Bernacki
 * 
 */
public class ChartHistoryEntry extends AbstractAction {
    /** The stacked symbol. */
    private final SymbolIdentity symbol;
    /** The stacked symbols' time frame. */
    private final TimeFrame timeFrame;
    
    
    /**
     * Constructs a new {@code SymbolStackElement} from the given parameters.
     * 
     * @param symbol
     *            the stacked symbol
     * @param timeFrame
     *            the stacked symbols' time frame
     */
    public ChartHistoryEntry(SymbolIdentity symbol, TimeFrame timeFrame) {
        super(symbol + " @ " + timeFrame);
        this.symbol = symbol;
        this.timeFrame = timeFrame;
    }
    
    /**
     * @return the symbol
     */
    public SymbolIdentity getSymbol() {
        return symbol;
    }
    
    /**
     * @return the timeFrame
     */
    public TimeFrame getTimeFrame() {
        return timeFrame;
    }
    
    private static ChartFrame getChartFrameAncestor(Object source) {
        if (source instanceof Container) {
            Container cont = (Container) source;
            do {
                if (cont instanceof ChartFrame)
                    return (ChartFrame) cont;
            } while ((cont = cont.getParent()) != null);
        }
        return null;
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        ChartFrame chartFrame = getChartFrameAncestor(e.getSource());
        if (chartFrame != null)
            chartFrame.navigationChange(this);
    }
}
