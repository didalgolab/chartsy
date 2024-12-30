/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.action;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;


public class ButtonAction extends AbstractAction {
    /** The button wrapped around this action. */
    private final AbstractButton button;
    
    
    public ButtonAction(AbstractButton button) {
        this.button = button;
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        AWTEvent nextKeyEvent = Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent(KeyEvent.KEY_PRESSED);
        if (nextKeyEvent != null)
            button.doClick(0);
        else
            button.doClick();
    }
}
