/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui;

import one.chartsy.Symbol;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.ui.chart.ChartTemplateCatalog;
import one.chartsy.ui.chart.ChartTemplateSummary;
import one.chartsy.ui.chart.components.ChartTemplateManagerDialog;
import one.chartsy.ui.chart.components.SymbolSelectorArea;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.TextAttribute;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;

public class NewChartDialog extends JDialog {

    private final transient ResourceBundle rb = NbBundle.getBundle(UI.class);
    private final ChartTemplateCatalog templateCatalog = ChartTemplateCatalog.getDefault();

    private SymbolSelectorArea symbolSelector;
    private List<Symbol> selectedSymbols;

    private DataProvider dataProvider;
    private String messageUrl = "";
    private List<ChartTemplateSummary> availableTemplates = List.of();
    private ChartOpenOptions chartOpenOptions = ChartOpenOptions.DEFAULT;

    public NewChartDialog(Frame parent, boolean modal) {
        this(parent, modal, null);
    }

    public NewChartDialog(Frame parent, boolean modal, DataProvider dataProvider) {
        super(parent, modal);

        setTitle(rb.getString("NewChartDialog.title"));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setIconImage(WindowManager.getDefault().getMainWindow().getIconImage());
        initComponents();
        getRootPane().setDefaultButton(openButton);
        initForm(dataProvider);
    }

    public void setAvailableTemplates(List<ChartTemplateSummary> templates) {
        availableTemplates = List.copyOf(Objects.requireNonNull(templates, "templates"));
        templateChoice.setModel(new DefaultComboBoxModel<>(availableTemplates.toArray(ChartTemplateSummary[]::new)));
        templateChoice.setEnabled(!availableTemplates.isEmpty());
    }

    public List<ChartTemplateSummary> getAvailableTemplates() {
        return List.copyOf(availableTemplates);
    }

    public void setDefaultTemplate(UUID templateKey) {
        if (templateKey == null) {
            if (!availableTemplates.isEmpty())
                templateChoice.setSelectedIndex(0);
            return;
        }

        for (int i = 0; i < availableTemplates.size(); i++) {
            if (templateKey.equals(availableTemplates.get(i).templateKey())) {
                templateChoice.setSelectedIndex(i);
                return;
            }
        }
    }

    public Optional<ChartTemplateSummary> getDefaultTemplate() {
        return availableTemplates.stream().filter(ChartTemplateSummary::defaultTemplate).findFirst();
    }

    public ChartOpenOptions getChartOpenOptions() {
        return chartOpenOptions;
    }

    private void initForm(DataProvider dataProvider) {
        messageLabel.setForeground(Color.red);
        messageLabel.setVisible(false);
        updateMessageLabelCursor();

        if (dataProvider == null) {
            Collection<? extends DataProvider> providers = Lookup.getDefault().lookupAll(DataProvider.class);
            dataProvider = providers.iterator().next();
            for (DataProvider provider : providers)
                dataProviderChoice.addItem(provider);
            dataProviderChoice.setMaximumRowCount(providers.size());
            dataProviderChoice.setSelectedItem(dataProvider);
        } else {
            dataProviderChoice.addItem(dataProvider);
            dataProviderChoice.setEnabled(false);
        }
        this.dataProvider = dataProvider;

        symbolSelector.setDataProvider(dataProvider);
        refreshTemplateChoices(null);
    }

    public DataProvider getDataProvider() {
        return dataProvider;
    }

    private void initComponents() {
        dataProviderChoice = new JComboBox<>();
        templateChoice = new JComboBox<>();
        manageTemplatesButton = new JButton();
        openButton = new JButton();
        cancelButton = new JButton();

        setTitle(rb.getString("NewChartDialog.title"));

        JLabel dataProviderLabel = new JLabel(rb.getString("NewChartDialog.dataProvider"));
        Font bolderFont = dataProviderLabel.getFont().deriveFont(
                java.util.Collections.singletonMap(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD));
        dataProviderLabel.setFont(bolderFont);
        JLabel templateLabel = new JLabel(rb.getString("NewChartDialog.template"));
        templateLabel.setFont(bolderFont);

        dataProviderChoice.addActionListener(this::onDataProviderChange);
        templateChoice.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String label = "";
                if (value instanceof ChartTemplateSummary template) {
                    label = template.name();
                    if (template.defaultTemplate())
                        label += " (Default)";
                }
                return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
            }
        });
        manageTemplatesButton.setText(rb.getString("NewChartDialog.manageTemplates"));
        manageTemplatesButton.addActionListener(this::onManageTemplates);

        openButton.setText(rb.getString("NewChartDialog.open"));
        openButton.addActionListener(this::onAccepted);

        cancelButton.setText(rb.getString("NewChartDialog.cancel"));
        cancelButton.addActionListener(this::onCancelled);

        messageLabel = new JLabel();
        messageLabel.setHorizontalAlignment(SwingConstants.LEFT);
        messageLabel.setText(rb.getString("NewChartDialog.msgLabel.text"));
        messageLabel.setHorizontalTextPosition(SwingConstants.LEFT);
        messageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                onMessageClicked(evt);
            }
        });

        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        Container pane = getContentPane();
        pane.setLayout(gridbag);

        c.insets = new Insets(5, 10, 1, 10);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.ipadx = c.ipady = 2;
        c.weightx = 0.0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        symbolSelector = new SymbolSelectorArea();
        pane.add(symbolSelector, c);

        c.insets = new Insets(1, 10, 1, 10);
        c.weightx = 0.0;
        c.gridwidth = 1;
        pane.add(dataProviderLabel, c);
        c.gridwidth = GridBagConstraints.REMAINDER;
        pane.add(dataProviderChoice, c);
        c.gridwidth = 1;
        pane.add(templateLabel, c);
        c.weightx = 1.0;
        c.gridwidth = 1;
        pane.add(templateChoice, c);
        c.weightx = 0.0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        pane.add(manageTemplatesButton, c);
        c.insets = new Insets(0, 10, 4, 10);
        c.weightx = 1.0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        pane.add(messageLabel, c);
        c.insets = new Insets(1, 10, 1, 10);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(openButton);
        buttons.add(cancelButton);
        pane.add(buttons, c);

        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                symbolSelector.getTextComponent().requestFocusInWindow();
            }
        });
        pack();
    }

    @SuppressWarnings("serial")
    private final Action closeAction = new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent event) {
            dispatchEvent(new WindowEvent(
                    NewChartDialog.this, WindowEvent.WINDOW_CLOSING
            ));
        }
    };

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.width = Math.max(size.width, size.height * 13 / 10);
        return size;
    }

    protected void onDataProviderChange(ActionEvent evt) {
        JComboBox<DataProvider> source = (JComboBox<DataProvider>) evt.getSource();
        DataProvider provider = (DataProvider) source.getSelectedItem();
        this.dataProvider = provider;
        if (symbolSelector != null)
            symbolSelector.setDataProvider(provider);
    }

    protected void onAccepted(ActionEvent e) {
        symbolSelector.getAutocompleter().disableTimer();
        dataProvider = (DataProvider) dataProviderChoice.getSelectedItem();
        selectedSymbols = symbolSelector.getSelectedSymbols(null)
                .stream()
                .map(symb -> new Symbol(symb, dataProvider))
                .toList();
        chartOpenOptions = new ChartOpenOptions(null, getSelectedTemplateKey());

        setVisible(false);
    }

    protected void onCancelled(ActionEvent e) {
        this.selectedSymbols = List.of();
        this.dataProvider = null;
        this.chartOpenOptions = ChartOpenOptions.DEFAULT;
        setVisible(false);
    }

    private void onManageTemplates(ActionEvent event) {
        if (!manageTemplatesButton.isEnabled())
            return;

        UUID preferredTemplateKey = getSelectedTemplateKey();
        ChartTemplateManagerDialog dialog = new ChartTemplateManagerDialog(this, preferredTemplateKey);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        UUID selectedTemplateKey = Optional.ofNullable(dialog.getSelectedTemplate())
                .map(ChartTemplateSummary::templateKey)
                .orElse(preferredTemplateKey);
        refreshTemplateChoices(selectedTemplateKey);
    }

    private void onMessageClicked(MouseEvent evt) {
        if (!messageUrl.isEmpty()) {
            try {
                // DesktopUtil.browse(messageUrl);
            } catch (Exception ex) {
            }
        }
    }

    public List<Symbol> getSelectedSymbols() {
        return selectedSymbols;
    }

    private UUID getSelectedTemplateKey() {
        Object selectedItem = templateChoice.getSelectedItem();
        return (selectedItem instanceof ChartTemplateSummary template) ? template.templateKey() : null;
    }

    private void refreshTemplateChoices(UUID preferredTemplateKey) {
        try {
            setAvailableTemplates(templateCatalog.listTemplates());
            manageTemplatesButton.setEnabled(true);
            hideTemplateMessage();
        } catch (RuntimeException ex) {
            setAvailableTemplates(List.of(ChartTemplateCatalog.builtInSummary()));
            manageTemplatesButton.setEnabled(false);
            showTemplateMessage(rb.getString("NewChartDialog.templateUnavailable"));
        }
        UUID templateKeyToSelect = (preferredTemplateKey != null)
                ? preferredTemplateKey
                : getDefaultTemplate().map(ChartTemplateSummary::templateKey).orElse(null);
        setDefaultTemplate(templateKeyToSelect);
    }

    private void showTemplateMessage(String message) {
        messageUrl = "";
        messageLabel.setText(message);
        messageLabel.setVisible(true);
        updateMessageLabelCursor();
        getContentPane().revalidate();
        getContentPane().repaint();
        pack();
    }

    private void hideTemplateMessage() {
        messageUrl = "";
        messageLabel.setVisible(false);
        updateMessageLabelCursor();
        getContentPane().revalidate();
        getContentPane().repaint();
        pack();
    }

    private void updateMessageLabelCursor() {
        messageLabel.setCursor(messageUrl.isBlank()
                ? Cursor.getDefaultCursor()
                : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private JButton cancelButton;
    private JButton manageTemplatesButton;
    private JButton openButton;
    private JComboBox<DataProvider> dataProviderChoice;
    private JComboBox<ChartTemplateSummary> templateChoice;
    private JLabel messageLabel;
}
