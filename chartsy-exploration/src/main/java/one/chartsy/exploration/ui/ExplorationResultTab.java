package one.chartsy.exploration.ui;

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
    private final ExplorationResultTable resultTable;
    /** The summary table where the summary rows are displayed. */
    private final JLabel summaryTextLabel;


    /**
     * Creates new form ExplorationResultTab
     *
     */
    public ExplorationResultTab(String name) {
        initComponents();
        setName(name);
        resultTable = new ExplorationResultTable();
        resultTable.setName(name);
        scrollPane.setViewportView(resultTable);
        scrollPane.setBorder(null);
        scrollPane.setViewportBorder(null);

        summaryTextLabel = new JLabel();
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
