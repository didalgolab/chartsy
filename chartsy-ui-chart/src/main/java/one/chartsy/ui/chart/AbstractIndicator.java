/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.ui.chart.data.VisualRange;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

public abstract class AbstractIndicator extends Indicator {
    
    @Parameter(name = "Marker Visibility")
    public boolean markerVisibility;
    
    
    protected AbstractIndicator(String name) {
        super(name);
    }
    
    @Override
    public String getLabel() {
        return getName();
    }
    
    @Override
    public Indicator newInstance() {
        try {
            return getClass().getDeclaredConstructor().newInstance();
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | SecurityException | InvocationTargetException e) {
            throw new RuntimeException("Cannot instantiate " + getClass().getSimpleName(), e);
        }
    }
    
    @Override
    public boolean getMarkerVisibility() {
        return markerVisibility;
    }
    
    /** The steps definition used by this indicators' scale. */
    private final StepsDefinition stepsDefinition = new StepsDefinition();
    
    @Override
    public double[] getStepValues(ChartContext cf) {
        VisualRange range = getRange(cf);
        
        final int NUMBER_OF_STEPS = 5;
        return stepsDefinition.calculateStepValues(range.getMin(), range.getMax(), NUMBER_OF_STEPS);
    }
    
    static class StepsDefinition implements java.io.Serializable {
        /**
         * The table of preferred tick marks to be used by this
         * {@code StepsDefinition}.
         * <p>
         * Represented by a table of digits in a range between {@code 1} and
         * {@code 10} (both inclusive), representing subsequent possible ticks
         * positions on an axis scale. The provided table of digits <b>must
         * be</b> in ascending order.
         */
        private final double[] stepsDefinition;
        
        /**
         * The default table of tick marks.
         * <p>
         * The following table of mark digits is used by default:<br />
         * {@code 1, 2, 2.5, 3, 4, 5, 6, 7, 7.5, 8, 9, 10}}.
         */
        private static final double[] DEFAULT_STEPS_DEFINITION = {1, 2, 2.5, 3, 4, 5, 6, 7, 7.5, 8, 9, 10};
        
        /**
         * Constructs a new {@code StepsDefinition} using the {@link #DEFAULT_STEPS_DEFINITION} table as a steps definition.
         */
        public StepsDefinition() {
            this.stepsDefinition = DEFAULT_STEPS_DEFINITION;
        }
        
        /**
         * Constructs a new {@code StepsDefinition} using the given
         * {@code tickMarks} table.
         * 
         * @param stepsDefinition
         *            the tick marks table, it's cloned before use
         */
        public StepsDefinition(double[] stepsDefinition) {
            this.stepsDefinition = stepsDefinition.clone();
        }

        /**
         * Returns the step values displayed on the scale.
         */
        public double[] calculateStepValues(double min, double max, int n) {
            double range = max - min;
            double unroundedTickSize = range/(n - 1);
            double exponent = Math.ceil(Math.log10(unroundedTickSize) - 1);
            double pow10x = Math.pow(10, exponent);
            
            double residual = unroundedTickSize / pow10x;
            int x = Arrays.binarySearch(stepsDefinition, residual);
            x = Math.max(x, Math.min(-x, stepsDefinition.length) - 1);
            
            double stepSize = stepsDefinition[x] * pow10x;
            double minMark = stepSize * Math.ceil(min / stepSize);
            double maxMark = stepSize * Math.floor(max / stepSize);
            
            int markCount = Math.toIntExact(Math.round(1.0 + (maxMark - minMark) / stepSize));
            double[] marks = new double[markCount];
            for (int i = 0; i < marks.length; i++)
                marks[i] = minMark + i * stepSize;
            return marks;
        }
    }
}
