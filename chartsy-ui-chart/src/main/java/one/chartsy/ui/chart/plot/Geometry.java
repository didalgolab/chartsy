/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.plot;

import java.util.ArrayList;
import java.util.BitSet;

public class Geometry {
    /** The point coordinates in this geometry. */
    private final double[] coords;
    /** The number of points. */
    private int numOfCoords;
    
    
    public Geometry(int capacity) {
        coords = new double[capacity];
    }
    
    public void simplify(double sqTolerance) {
        int numOfPoints = numOfCoords / 2;
        
        BitSet markers = new BitSet(numOfPoints);
        
        Integer first = 0;
        Integer last = numOfPoints - 1;
        
        int index = 0;
        
        ArrayList<Integer> firstStack = new ArrayList<>();
        ArrayList<Integer> lastStack = new ArrayList<>();
        
        ArrayList<Float> newPoints = new ArrayList<>();
        
        markers.set(first);
        markers.set(last);
        
        while (last != null) {
            double maxSqDist = 0;
            
            for (int i = first + 1; i < last; i++) {
                double sqDist = getSquareSegmentDistance(i << 1, first << 1,
                        last << 1);
                
                if (sqDist > maxSqDist) {
                    index = i;
                    maxSqDist = sqDist;
                }
            }
            
            if (maxSqDist > sqTolerance) {
                markers.set(index);
                
                firstStack.add(first);
                lastStack.add(index);
                
                firstStack.add(index);
                lastStack.add(last);
            }
            
            if (firstStack.size() > 1)
                first = firstStack.remove(firstStack.size() - 1);
            else
                first = null;
            
            if (lastStack.size() > 1)
                last = lastStack.remove(lastStack.size() - 1);
            else
                last = null;
        }
        
        //		for (int i = markers.nextSetBit(0); i >= 0; i = markers.nextSetBit(i+1)) {
        
        //            	newPoints.add(coords[i << 1]);
        //				newPoints.add(coords[(i << 1) + 1]);
        //		}
        
        //		return newPoints.toArray(new Float[newPoints.size()]);
    }
    
    private double getSquareSegmentDistance(int p, int p1, int p2) {
        double[] coords = this.coords;
        double x = coords[p1], y = coords[p1 + 1];
        double dx = coords[p2] - x, dy = coords[p2 + 1] - y;
        
        if (dx != 0 || dy != 0) {
            double t = ((coords[p] - x)*dx + (coords[p + 1] - y)*dy) / (dx*dx + dy*dy);
            
            if (t > 1) {
                x = coords[p2];
                y = coords[p2 + 1];
            } else if (t > 0) {
                x += dx * t;
                y += dy * t;
            }
        }
        dx = coords[p] - x;
        dy = coords[p + 1] - y;
        
        return dx*dx + dy*dy;
    }
}
