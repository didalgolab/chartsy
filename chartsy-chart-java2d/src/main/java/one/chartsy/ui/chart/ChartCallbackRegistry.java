/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart;

import one.chartsy.SymbolIdentity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The registry holding custom {@link ChartCallback's}.
 * 
 * @author Mariusz Bernacki
 */
public class ChartCallbackRegistry {
    
    /** The registry holding tags and callback pairs for a particular symbol. */
    private final Map<SymbolIdentity, Invoker<?>> registry = new HashMap<>();
    
    
    /**
     * Adds a {@link ChartCallback} to this registry that will be invoked when a
     * chart with a particular {@code symbol} is opened. When {@code null} is
     * provided as {@code symbol} argument, then the given {@code callback} is
     * registered to all symbols.
     * 
     * @param symbol
     *            the symbol to be associated with the callback, may be
     *            {@code null}
     * @param tag
     *            the object that will be passed to the
     *            {@link ChartCallback#onChart(ChartFrame, Object)} method
     * @param callback
     *            the chart callback
     */
    public synchronized <T> void addChartCallback(SymbolIdentity symbol, T tag, ChartCallback<T> callback) {
        // wrap tag and callback with an invoker object
        Invoker<?> invoker = new Invoker<>(tag, callback);
        
        // add invoker object to the registry
        Invoker<?> invokers = registry.get(symbol);
        if (invokers == null)
            registry.put(symbol, invoker);
        else
            invokers.addNext(invoker);
    }
    
    /**
     * Determines whether this registry contains no added chart callbacks.
     *
     * @return {@code true} if this registry contains no added chart callbacks
     */
    public boolean isEmpty() {
        return registry.isEmpty();
    }
    
    /**
     * Fires the {@link ChartCallback#onChart(ChartFrame, Object)} events
     * for the given chart and associated symbol.
     * 
     * @param chart
     *            the chart opened
     */
    public void fireOnChart(ChartFrame chart) {
        // get the symbol being shown
        SymbolIdentity symbol = chart.getChartData().getSymbol();
        
        // invoke registered callbacks
        Invoker<?> invoker = registry.get(symbol);
        if (invoker != null)
            invoker.onChart(chart);
        
        // invoke registered 'for-all-symbols' callbacks
        invoker = registry.get(null);
        if (invoker != null)
            invoker.onChart(chart);
        
    }
    
    public static class Invoker<T> {
        /** The tag associated with this callback invocation. */
        private final T tag;
        /** The target callback to be invoked. */
        private final ChartCallback<T> callback;
        /** Optional next callback's chained for the same symbol (instantiated as needed) */
        private volatile List<Invoker<?>> nextInvokers;
        
        
        protected Invoker(T tag, ChartCallback<T> callback) {
            this.tag = tag;
            this.callback = callback;
        }
        
        protected void onChart(ChartFrame frame) {
            // invoke callback associated with this invoker
            callback.onChart(frame, tag);
            
            // invoke next chained invokers
            List<Invoker<?>> nextInvokers = this.nextInvokers;
            if (nextInvokers != null)
                for (Invoker<?> next : nextInvokers)
                    next.onChart(frame);
        }
        
        protected synchronized void addNext(Invoker<?> invoker) {
            List<Invoker<?>> nextInvokers = this.nextInvokers;
            if (nextInvokers == null)
                nextInvokers = this.nextInvokers = new CopyOnWriteArrayList<>();
            nextInvokers.add(invoker);
        }
    }
}
