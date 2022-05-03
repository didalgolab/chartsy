/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
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
