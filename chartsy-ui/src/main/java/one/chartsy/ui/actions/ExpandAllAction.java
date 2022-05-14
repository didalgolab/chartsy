/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.actions;

import org.openide.util.ImageUtilities;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class ExpandAllAction extends AbstractAction {

    public ExpandAllAction() {
        putValue(SHORT_DESCRIPTION, "Expand All");
        putValue(SMALL_ICON, ImageUtilities.loadImageIcon("/one/chartsy/ui/resources/expand-all.png", true));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        System.out.println(e.getSource());
        System.out.println(e.getSource().getClass());
    }
}
