/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.internal;

import java.awt.Color;
import java.util.concurrent.ThreadLocalRandom;

import org.openide.util.Lookup;

/**
 * Provides the color utility services for the graphical user interface.
 *
 * @author Mariusz Bernacki
 * 
 */
public class ColorServices {
    
    public static ColorServices getDefault() {
        ColorServices repo = Lookup.getDefault().lookup(ColorServices.class);
        return (repo != null)? repo : Holder.INSTANCE;
    }
    
    protected ColorServices() {
    }
    
    public Color getRandomColor() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new Color(r.nextInt(256), r.nextInt(256), r.nextInt(256));
    }
    
    public Color getTransparentColor(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
    
    /**
     * The default instance holder.
     * 
     * @author Mariusz Bernacki
     *
     */
    static final class Holder {
        /** The shared default instance of the color services. */
        static final ColorServices INSTANCE = new ColorServices();
    }
}
