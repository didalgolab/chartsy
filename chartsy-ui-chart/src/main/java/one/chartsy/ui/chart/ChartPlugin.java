/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import java.beans.PropertyEditor;
import java.io.Serial;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedHashMap;
import java.util.Map;

import one.chartsy.core.NamedPlugin;
import one.chartsy.core.ObjectInstantiator;

public abstract class ChartPlugin<T extends ChartPlugin<T>> extends NamedPlugin<T> implements ChartFrameListener, ObjectInstantiator<T> {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Parameter {
        /** The parameter display name. */
        String name();
        /** An optional parameter description. */
        String description() default "";
        /** The custom property editor class. */
        Stereotype stereotype() default Stereotype.NONE;
        /** The custom property editor class. */
        Class<? extends PropertyEditor> propertyEditorClass() default PropertyEditor.class;
    }

    public enum Stereotype {
        NONE,
        TRANSPARENCY;
    }

    protected ChartPlugin(String name) {
        super(name);
    }

    public abstract String getLabel();

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
