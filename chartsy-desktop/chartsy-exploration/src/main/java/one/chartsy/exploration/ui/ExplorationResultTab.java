/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.exploration.ui;

import one.chartsy.TimeFrame;
import one.chartsy.kernel.ExplorationFragment;
import one.chartsy.ui.ChartManager;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.windows.TopComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;

/**
 * The component displaying results of the exploration action.
 *
 * @author Mariusz Bernacki
 *
 */
@TopComponent.Description(
        preferredID = "ExplorationResultTab"
        , iconBase = "one/chartsy/exploration/ui/resources/binoculars.png"
        , persistenceType = TopComponent.PERSISTENCE_NEVER
)
public class ExplorationResultTab extends TopComponent {
    /** The master table where the exploration result list is displayed. */
    private final ExplorationResultTable resultTable = new ExplorationResultTable();


    /**
     * Creates new form ExplorationResultTab
     *
     */
    public ExplorationResultTab(String name) {
        initComponents();
        setName(name);
        resultTable.setName(name);
        resultTable.setComponentPopupMenu(constructPopupMenu());
        scrollPane.setViewportView(resultTable);
        scrollPane.setBorder(null);
        scrollPane.setViewportBorder(null);

        associateLookup(Lookups.fixed(resultTable));
    }

    public ExplorationResultTable getResultTable() {
        return resultTable;
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));

        scrollPane = new JScrollPane();
        scrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JScrollPane scrollPane;

    protected JPopupMenu constructPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.putClientProperty(JTable.class, this.resultTable);
        popupMenu.add(new AbstractAction("Open Chart") {
            @Override
            public void actionPerformed(ActionEvent e) {
                var selectedSymbols = Arrays.stream(resultTable.getSelectedRows())
                        .map(resultTable::convertRowIndexToModel)
                        .mapToObj(rowId -> resultTable.getModel().getRowAt(rowId))
                        .map(ExplorationFragment::symbol)
                        .toList();

                Lookup.getDefault().lookup(ChartManager.class)
                        .open(selectedSymbols, TimeFrame.Period.DAILY, null);
            }
        });
        //popupMenu.add(org.openide.awt.Actions.forID("File", "com.softignition.chartsy.actions.ChartsyActions.TabDelimitedExportAction"));
        //popupMenu.add(org.openide.awt.Actions.forID("File", "com.softignition.chartsy.actions.ChartsyActions.ExcelCSVExportAction"));
        //popupMenu.add(TableActions.rowCountAction);

        return popupMenu;
    }
}
