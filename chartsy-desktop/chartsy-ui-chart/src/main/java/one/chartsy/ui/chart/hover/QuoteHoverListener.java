/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.hover;

import java.util.Collection;

import org.openide.util.Lookup;

/**
 * The interface for receiving events when a quote is hovered by the mouse.
 * 
 * @author Mariusz Bernacki
 * 
 */
public interface QuoteHoverListener extends HoverListener {
    
    /**
     * A {@code Broadcaster} is responsible for delivering messages to all {@link QuoteHoverListener}s, which are
     * registered in the global lookups of this application.
     */
    public static final QuoteHoverListener Broadcaster = new QuoteHoverListener() {
        
        protected Collection<? extends QuoteHoverListener> getListeners() {
            return Lookup.getDefault().lookupAll(QuoteHoverListener.class);
        }
        
        @Override
        public void mouseEntered(HoverEvent event) {
            for (QuoteHoverListener l : getListeners())
                l.mouseEntered(event);
        }
        
        @Override
        public void mouseExited(HoverEvent event) {
            for (QuoteHoverListener l : getListeners())
                l.mouseExited(event);
        }
    };
}
