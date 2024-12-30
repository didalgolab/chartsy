/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.properties.editor;

import one.chartsy.ui.chart.BasicStrokes;

import java.awt.Dimension;
import java.awt.Stroke;

import javax.swing.JComboBox;

public class StrokeComboBox extends JComboBox {
    
    public StrokeComboBox() { this(BasicStrokes.getStrokes(), 100, 30); }
    
    public StrokeComboBox(Stroke[] strokes, int width, int height) {
        super(strokes);
        setRenderer(new StrokeComboBoxRenderer(width, height));
        Dimension prefSize = getPreferredSize();
        prefSize.height = height + getInsets().top + getInsets().bottom;
        setPreferredSize(prefSize);
        setMaximumRowCount(10);
    }
    
}
