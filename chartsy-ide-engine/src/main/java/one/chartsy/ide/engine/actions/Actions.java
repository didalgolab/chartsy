package one.chartsy.ide.engine.actions;

import org.openide.util.NbBundle;

import javax.swing.*;

public class Actions {

    public static Action runConfiguration() {
        Action a = new LauncherAction(
                "run",
                NbBundle.getMessage(Actions.class, "RunConfiguration"),null, null);
        a.putValue("iconBase","one/chartsy/ide/engine/resources/runProject.png");
        return a;
    }
}
