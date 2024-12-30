/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.misc;

import java.awt.*;
import java.text.Format;
import java.util.Optional;

/**
 * A value object associated with an optional style.
 *
 * @author Mariusz Bernacki
 *
 */
public class StyledValue implements Comparable<StyledValue> {
    /** The number value of the object. */
    private final Number numberValue;
    /** The string value of the object. */
    private final String stringValue;
    /** The background color of the current cell. */
    private final Color background;
    /** The foreground value of the current cell. */
    private final Color foreground;


    @SuppressWarnings("unchecked")
    public <T> Optional<T> getStyle(StyleOption<T> option) {
        if (option == StyleOption.BACKGROUND)
            return Optional.ofNullable((T)background);
        if (option == StyleOption.FOREGROUND)
            return Optional.ofNullable((T)foreground);

        return Optional.empty();
    }

    public Number numberValue() {
        return numberValue;
    }
    
    public String stringValue() {
        return stringValue;
    }
    
    @Override
    public int compareTo(StyledValue that) {
        Number n1 = this.numberValue;
        Number n2 = that.numberValue;
        if (n1 != null && n2 != null)
            return Double.compare(n1.doubleValue(), n2.doubleValue());
        if (n1 != null)
            return -1;
        if (n2 != null)
            return 1;

        return this.toString().compareTo(that.toString());
    }
    
    @Override
    public String toString() {
        if (stringValue != null)
            return stringValue;
        if (numberValue != null)
            return numberValue.toString();
        return "";
    }
    
    public Object toRawValue() {
        if (numberValue != null)
            return numberValue;
        return stringValue;
    }

    public static StyledValue of(Object value) {
        return of(value, null, null, null);
    }

    public static StyledValue of(Object value, Format format) {
        return of(value, format, null, null);
    }

    public static StyledValue of(Object value, Format format, Color bgColor) {
        return of(value, format, bgColor, null);
    }

    public static StyledValue of(Object value, Format format,
                                 Color bgColor, Color fgColor) {
        Number numberValue = null;
        String stringValue = null;
        
        if (value != null) {
            // use supplied formatter to convert given value to string
            if (format != null) {
                try {
                    stringValue = format.format(value);
                } catch (IllegalArgumentException e) {
                    stringValue = value.toString();
                }
            } else {
                stringValue = value.toString();
            }
            
            // try convert value to number object
            if (value instanceof Number) {
                numberValue = (Number) value;
            } else {
                try {
                    numberValue = Double.parseDouble(stringValue);
                    if (numberValue.doubleValue() == (double) numberValue.intValue())
                        numberValue = numberValue.intValue();
                    else if (numberValue.doubleValue() == (double) numberValue.longValue())
                        numberValue = numberValue.longValue();
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }

        return new StyledValue(numberValue, stringValue, fgColor, bgColor);
    }

    private StyledValue(Number numberValue, String stringValue, Color fgColor, Color bgColor) {
        this.numberValue = numberValue;
        this.stringValue = stringValue;
        this.foreground = fgColor;
        this.background = bgColor;
    }
}
