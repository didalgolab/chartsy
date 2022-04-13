package one.chartsy.exploration.ui;

import org.openide.util.lookup.Lookups;
import org.openide.windows.TopComponent;

import javax.swing.*;
import java.awt.*;

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
}
