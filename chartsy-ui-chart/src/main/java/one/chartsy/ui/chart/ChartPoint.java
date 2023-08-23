/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;

/**
 * Represents a coordinate of a point on the chart.
 * <p>
 * The coordinate is composed of the time and price value which may be
 * translated into a visual {@code (x, y)} coordinates when needed to draw an
 * object with particular coordinates.
 * 
 * @author Mariusz Bernacki
 *
 */
public final class ChartPoint implements Serializable {
    /** The "real" time coordinate part. */
    private long time;
    /** The price or value coordinate part. */
    private double value;
    
    
    public ChartPoint() {
        this(0, 0);
    }
    
    public ChartPoint(long time, double value) {
        this.time = time;
        this.value = value;
    }
    
    /**
     * Returns the chart point time
     *
     * @return the time
     */
    public long getTime() {
        return time;
    }
    
    /**
     * Returns the chart point price value.
     * 
     * @return the value
     */
    public double getValue() {
        return value;
    }
    
    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeLong(time);
        out.writeDouble(value);
    }
    
    @Serial
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        time = in.readLong();
        value = in.readDouble();
    }
}
