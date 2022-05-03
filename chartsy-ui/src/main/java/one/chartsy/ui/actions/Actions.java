package one.chartsy.ui.actions;

import one.chartsy.TimeFrame;
import one.chartsy.ui.ChartManager;
import one.chartsy.ui.NewChartDialog;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class Actions {

    public static Action newChart() {
        Action action = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                NewChartDialog dialog = new NewChartDialog(WindowManager.getDefault().getMainWindow(), true);
                dialog.setLocationRelativeTo(null);
                dialog.pack();
                dialog.setVisible(true);

                if (!dialog.isVisible()) {
                    var symbols = dialog.getSelectedSymbols();
                    Lookup.getDefault().lookup(ChartManager.class)
                            .open(symbols, TimeFrame.Period.DAILY);
                }
            }
        };
        action.putValue(Action.NAME, NbBundle.getMessage(Actions.class, "NewChart"));
        action.putValue("iconBase","one/chartsy/ui/resources/bar-chart-24.png");
        return action;
    }
}
