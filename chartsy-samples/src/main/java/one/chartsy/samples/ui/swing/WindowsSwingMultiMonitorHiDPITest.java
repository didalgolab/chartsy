/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.ui.swing;

import java.awt.Toolkit;
import javax.swing.*;

/**
 * Simple example of a Swing app that does not work correctly in a multi-monitor setup on Windows 10
 * involving one HiDPI screen (e.g. a Thinkpad Lenovo X1 carbon laptop with a 2560x1440 screen) and
 * a regular non-HiDPI external monitor (tested in this case with a 1920x1080 external monitor).
 *
 * <p>Tested on Java 10.0.2 and Java 11ea.
 */
public final class WindowsSwingMultiMonitorHiDPITest extends javax.swing.JFrame {
  /* Note: Toolkit.getDefaultToolkit() is an unreliable way to get HiDPI status, because different
           monitors may have different HiDPI scaling factors, and because getDefaultToolkit's value
           updates only when the user logs out and in again, not when the user changes the scaling
           setting in the Windows 10 control panel. Nevertheless, include the value here for
           debugging, in case its state is correlated with the bug. */
  private JCheckBox checkBox = new JCheckBox(System.getProperty("java.version") +
      " with toolkitDPI " + Toolkit.getDefaultToolkit().getScreenResolution());
  private JRadioButton radioButton = new JRadioButton("I am a radio button");

  public WindowsSwingMultiMonitorHiDPITest() {
    setTitle("Windows Swing HiDPI Test");
    setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
    getContentPane().add(checkBox, java.awt.BorderLayout.NORTH);
    getContentPane().add(radioButton, java.awt.BorderLayout.SOUTH);
    setSize(500, 200);
  }

  public static void main(String args[]) throws Exception {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    SwingUtilities.invokeLater(() -> {
      new WindowsSwingMultiMonitorHiDPITest().setVisible(true);
    });
  }
} 