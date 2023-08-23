/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.awt.BasicStroke;
import java.awt.Stroke;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 
 * @author Mariusz Bernacki
 */
public abstract class BasicStrokes {
    
    /** The solid line strokes. */
    public static final Stroke ULTRATHIN_SOLID = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
    public static final Stroke THIN_SOLID = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
    public static final Stroke SOLID = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
    public static final Stroke THICK_SOLID = new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
    public static final Stroke ULTRATHICK_SOLID = new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
    /** The dotted line strokes. */
    public static final Stroke ULTRATHIN_DOTTED = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, new float[] {1f}, 0.5f);
    public static final Stroke THIN_DOTTED = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {0f, 3f}, 0f);
    public static final Stroke DOTTED = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {0f, 4f}, 0f);
    public static final Stroke THICK_DOTTED = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {0f, 6f}, 0f);
    public static final Stroke ULTRATHICK_DOTTED = new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {0f, 8f}, 0f);
    /** The dashed line strokes. */
    public static final Stroke ULTRATHIN_DASHED = new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {9f, 6f}, 0f);
    public static final Stroke THIN_DASHED = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {9f, 7f}, 0f);
    public static final Stroke DASHED = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {9f, 8f}, 0f);
    public static final Stroke THICK_DASHED = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {9f, 10f}, 0f);
    public static final Stroke ULTRATHICK_DASHED = new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {9f, 12f}, 0f);
    /** The dot-dash line strokes. */
    public static final Stroke ULTRATHIN_DOT_DASH = new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {9f, 3f, 0f, 3f}, 0f);
    public static final Stroke THIN_DOT_DASH = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {9f, 4f, 0f, 4f}, 0f);
    public static final Stroke DOT_DASH = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {9f, 5f, 0f, 5f}, 0f);
    public static final Stroke THICK_DOT_DASH = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {9f, 7f, 0f, 7f}, 0f);
    public static final Stroke ULTRATHICK_DOT_DASH = new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[] {9f, 9f, 0f, 9f}, 0f);
    
    /** The default tiny line stroke. */
    public static final Stroke DEFAULT = SOLID;
    
    private static final Stroke[] strokes = { ULTRATHIN_SOLID,
            THIN_SOLID,
            SOLID,
            THICK_SOLID,
            ULTRATHICK_SOLID,
            ULTRATHIN_DOTTED,
            THIN_DOTTED,
            DOTTED,
            THICK_DOTTED,
            ULTRATHICK_DOTTED,
            ULTRATHIN_DASHED,
            THIN_DASHED,
            DASHED,
            THICK_DASHED,
            ULTRATHICK_DASHED,
            ULTRATHIN_DOT_DASH,
            THIN_DOT_DASH,
            DOT_DASH,
            THICK_DOT_DASH,
            ULTRATHICK_DOT_DASH
    };

    private static Map<BasicStroke, String> strokeNames = new HashMap<>();
    private static Map<String, BasicStroke> strokeMap = new HashMap<>();
    static {
        for (var field : BasicStrokes.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Stroke.class.isAssignableFrom(field.getType())) {
                try {
                    String strokeName = field.getName();
                    BasicStroke stroke = (BasicStroke) field.get(null);

                    strokeMap.put(strokeName, stroke);
                    strokeNames.put(stroke, strokeName);
                } catch (Exception e) {
                    throw new InternalError("BasicStrokes init failed", e);
                }
            }
        }
    }

    public static Optional<String> getStrokeName(Stroke stroke) {
        return Optional.ofNullable(strokeNames.get(stroke));
    }

    public static BasicStroke getStroke(String name) {
        return strokeMap.get(name);
    }

    private BasicStrokes() {
    }
    
    public static Stroke[] getStrokes() {
        return strokes;
    }
    
    public static int getStrokeIndex(Stroke stroke) {
        for (int i = 0; i < strokes.length; i++) {
            if (strokes[i].equals(stroke))
                return i;
        }
        return -1;
    }
    
    public static Stroke getStroke(int i) {
        return i != -1 ? strokes[i] : null;
    }
    
    public static boolean isStrokeIndex(int index) {
        return !(index < 0 || index > strokes.length - 1);
    }
    
}
