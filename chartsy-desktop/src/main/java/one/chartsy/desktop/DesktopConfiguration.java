/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.desktop;

import one.chartsy.ui.navigator.GlobalSymbolsTab;
import one.chartsy.ui.reports.ReportExplorer;
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

    @TopComponent.Registration(mode = "explorer", position = 152, openAtStartup = false)
    @TopComponent.OpenActionRegistration(displayName = "#ReportExplorer.name", preferredID = "ReportExplorer")
    @ActionID(category = "Window", id = "one.chartsy.desktop.ReportExplorer")
    @ActionReference(path = "Menu/Window")
    public static ReportExplorer getReportExplorer() {
        return ReportExplorer.getDefault();
    }
}
