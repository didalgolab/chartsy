package one.chartsy.desktop;

import one.chartsy.ui.navigator.GlobalSymbolsTab;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.windows.TopComponent;

public class DesktopConfiguration {

    @TopComponent.Registration(mode = "explorer", position = 151, openAtStartup = true)
    @TopComponent.OpenActionRegistration(displayName = "#SymbolsTab.name", preferredID = "GlobalSymbolsTab")
    @ActionID(category = "Window", id = "one.chartsy.desktop.navigator.GlobalSymbolsTab")
    @ActionReference(path = "Menu/Window")
    public static GlobalSymbolsTab getGlobalSymbolsTab() {
        return GlobalSymbolsTab.getDefault();
    }
}
