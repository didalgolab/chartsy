/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart;

import java.util.*;
import java.util.logging.Logger;

import one.chartsy.SymbolIdentity;
import one.chartsy.TimeFrame;
import one.chartsy.util.Pair;

/**
 * The stack of symbols for the purpose of chart undo/redo mechanism or the
 * symbol browsers.
 * 
 * @author Mariusz Bernacki
 */
public class ChartHistory implements java.io.Serializable {
    /** The logger instance. */
    protected static final Logger log = Logger.getLogger(ChartHistory.class.getPackage().getName());
    /** The previous elements on this stack. */
    //    private transient Stack<SymbolStackElement> backStack = new Stack<>();
    /** The forward elements on this stack. */ 
    //    private transient Stack<SymbolStackElement> forwardStack = new Stack<>();
    
    private final List<ChartHistoryEntry> elements = new ArrayList<>();
    private int position = -1;
    
    /** Indicates whether this {@code SymbolStack} tracks history changes. */
    private final boolean tracksHistory;
    
    
    /**
     * Constructs a new history tracking {@code SymbolStack}.
     */
    public ChartHistory() {
        tracksHistory = true;
    }
    
    /**
     * Constructs a non-tracking history {@code SymbolStack} with the provided
     * initial content.
     * 
     * @param symbols
     *            predefined symbols to save on the stack
     * @param timeFrame
     *            the time symbols time frame to be used when displaying any
     *            symbol from this {@code SymbolStack} on a chart
     */
    public ChartHistory(List<SymbolIdentity> symbols, TimeFrame timeFrame) {
        tracksHistory = false;
        for (int i = 0; i < symbols.size(); i++)
            elements.add(new ChartHistoryEntry(symbols.get(i), timeFrame));
        position = 0;
    }
    
    public ChartHistory(List<Pair<SymbolIdentity, TimeFrame>> data) {
        tracksHistory = false;
        for (int i = 0; i < data.size(); i++)
            elements.add(new ChartHistoryEntry(data.get(i).getLeft(), data.get(i).getRight()));
        position = 0;
    }
    
    /**
     * Called when the chart being displayed was changed to match the given
     * chart action.
     * <p>
     * The method is called by an owner of this {@code ChartNavigation} (usually
     * a {@link ChartFrame}) to inform that the displayed chart {@link SymbolIdentity}
     * or {@link TimeFrame} has been changed, either manually or
     * programmatically. It is up to an implementation of this method to either
     * ignore the specified chart change or record it on the navigation history
     * stack, that will be exposed later via the {@link #getPreviousActions()}
     * list.
     * 
     * @param action
     *            the chart action that occurred
     */
    public void actionPerformed(ChartHistoryEntry action) {
        if (tracksHistory) {
            ChartHistoryEntry current = (position < 0)? null : elements.get(position);
            if (current == null
                    || !Objects.equals(current.getSymbol(), action.getSymbol())
                    || !Objects.equals(current.getTimeFrame(), action.getTimeFrame())) {
                elements.add(++position, action);
                elements.subList(position + 1, elements.size()).clear();
            }
        }
    }
    
    // TODO: rename: canGoBack? hasPrevious?
    public boolean hasBackHistory() {
        return (position > 0);
    }
    
    // TODO rename: hasNext
    public boolean hasFwdHistory() {
        return (position < elements.size() - 1);
    }
    
    /**
     * Returns the list of previous available actions for this navigator.
     * 
     * @return the navigators previous actions
     */
    public List<ChartHistoryEntry> getPreviousActions() {
        return previousActions;
    }
    
    private final List<ChartHistoryEntry> previousActions = new AbstractList<>() {
        @Override
        public ChartHistoryEntry get(int index) {
            if (index < 0 || index > size())
                throw new IndexOutOfBoundsException("Index: " + index + ", size: " + size());
            
            return elements.get(index);
        }
        
        @Override
        public int size() {
            return Math.max(0, position);
        }
        
        @Override
        public void clear() {
            if (!isEmpty()) {
                elements.subList(0, position).clear();
                position = 0;
            }
        };
    };
    
    /**
     * Returns the list of next available actions for this navigator.
     * 
     * @return the navigators next actions
     */
    public List<ChartHistoryEntry> getNextActions() {
        int length = elements.size();
        return (position < length - 1)? elements.subList(position + 1, length) : Collections.emptyList();
    }
    
    public ChartHistoryEntry go(int steps) {
        int newPosition = this.position + steps;
        newPosition = Math.max(0, newPosition);
        newPosition = Math.min(elements.size() - 1, newPosition);
        this.position = newPosition;
        return elements.get(newPosition);
    }
}
