package one.chartsy.samples.ui;

import one.chartsy.ui.NewChartDialog;

import javax.swing.*;

public class NewChartDialogShowingExample {

    public static void main(String args[]) {
        //		try {
        //		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
        //				| UnsupportedLookAndFeelException e1) {
        //			// TODO Auto-generated catch block
        //			e1.printStackTrace();
        //		}
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            throw new RuntimeException(ex);
        }
        SwingUtilities.invokeLater(() -> {
            NewChartDialog dialog = new NewChartDialog(
                    new JFrame(), true);
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {

                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    System.exit(0);
                }
            });
            dialog.setVisible(true);
        });
    }
}
