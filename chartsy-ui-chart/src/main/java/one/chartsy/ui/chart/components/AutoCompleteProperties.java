/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.components;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AutoCompleteProperties {
    /** The delay in milliseconds between a keystroke and when the autocompleter widget displays the suggestion popup. */
    private int triggerDelay = 500;
    /** The maximum number of rows visible in the autocompleter widget. */
    private int visibleRowCount = 30;
    /** The maximum number of rows possible to store in the scrollable list. */
    private int maximumRowCount = 1000;
    /** The number of click counts required to fire the accept action for a list item. */
    private int acceptClickCount = 1;
    /** If the autocompletion options are case-sensitive. */
    private boolean caseSensitive;

}
