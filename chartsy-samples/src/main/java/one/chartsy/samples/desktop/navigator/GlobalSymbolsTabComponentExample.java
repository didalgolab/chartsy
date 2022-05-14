/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.desktop.navigator;

import one.chartsy.ui.navigator.GlobalSymbolsTab;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;

public class GlobalSymbolsTabComponentExample {

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        JFrame f = new JFrame();
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        f.getContentPane().add(GlobalSymbolsTab.getDefault());
        f.pack();

        SwingUtilities.invokeAndWait(() -> f.setVisible(true));
    }
}
