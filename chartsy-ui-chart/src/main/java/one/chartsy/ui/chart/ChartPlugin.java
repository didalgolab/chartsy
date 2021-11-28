/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart;

import java.beans.PropertyEditor;
import java.io.Serial;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedHashMap;
import java.util.Map;

import one.chartsy.core.NamedPlugin;
import one.chartsy.ui.chart.internal.PropertyEditorEnvironment;

public abstract class ChartPlugin<T extends ChartPlugin<T>> extends NamedPlugin<T> implements ChartFrameListener {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Parameter {
        /** The parameter display name. */
        String name();
        /** An optional parameter description. */
        String description() default "";
        /** The custom property editor class. */
        Stereotype stereotype() default Stereotype.NONE;
    }

    public enum Stereotype {
        NONE,
        TRANSPARENCY;
    }

    protected ChartPlugin(String name) {
        super(name);
    }

    public abstract String getLabel();

    public abstract T newInstance();
    
    protected static Map<String, Plot> createPlotsMap() {
        return new LinkedHashMap<>() {
            @Serial
            @SuppressWarnings("unchecked")
            private Object writeReplace() throws java.io.ObjectStreamException {
                Map<String, Plot> map = (Map<String, Plot>) clone();
                map.clear();
                return map;
            }
        };
    }
}
