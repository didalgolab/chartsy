/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.internal;

import javax.swing.text.*;

/**
 * The {@code DocumentFilter} that converts all characters to upper case before
 * they are inserted into the Document.
 *
 * @author Mariusz Bernacki
 */
public class UpperCaseDocumentFilter extends DocumentFilter {
    
    @Override
    public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs) throws BadLocationException {
        super.insertString(fb, offset, text.toUpperCase(), attrs);
    }
    
    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
        super.replace(fb, offset, length, text.toUpperCase(), attrs);
    }

    public static void decorate(JTextComponent component) {
        ((AbstractDocument) component.getDocument()).setDocumentFilter(new UpperCaseDocumentFilter());
    }
}
