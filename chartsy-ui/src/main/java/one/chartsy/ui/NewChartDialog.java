package one.chartsy.ui;

import one.chartsy.AssetTypes;
import one.chartsy.Symbol;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.ui.chart.components.SymbolSelectorArea;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.util.*;
import java.util.List;

public class NewChartDialog extends JDialog {

    private final ResourceBundle rb = NbBundle.getBundle(UI.class);

    private SymbolSelectorArea symbolSelector;
    private List<Symbol> selectedSymbols;

    private DataProvider dataProvider;
    private String messageUrl = "";


    /**
     * Constructs a new open chart dialog from the specified parameters.
     *
     * @param parent
     *            the parent frame
     * @param modal
     *            the modality of the dialog
     */
    public NewChartDialog(Frame parent, boolean modal) {
        this(parent, modal, null);
    }

    /**
     * Constructs a new open chart dialog from the specified parameters.
     *
     * @param parent
     *            the parent frame
     * @param modal
     *            the modality of the dialog
     * @param dataProvider
     *            the read-only data provider to be associated with the dialog
     */
    public NewChartDialog(Frame parent, boolean modal, DataProvider dataProvider) {
        super(parent, modal);

        setTitle(rb.getString("NewChartDialog.title"));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setIconImage(WindowManager.getDefault().getMainWindow().getIconImage());
        initComponents();
        getRootPane().setDefaultButton(openButton);
        initForm(dataProvider);
    }

    private List<String> availableTemplates = List.of("Default");
    private String defaultTemplate;

    public void setAvailableTemplates(java.util.List<String> templates) {
        this.availableTemplates = Objects.requireNonNull(templates);
    }

    public java.util.List<String> getAvailableTemplates() {
        return List.copyOf(availableTemplates);
    }

    public void setDefaultTemplate(String defaultTemplate) {
        this.defaultTemplate = defaultTemplate;
    }

    public Optional<String> getDefaultTemplate() {
        return Optional.ofNullable(defaultTemplate);
    }

    private void initForm(DataProvider dataProvider) {
        messageLabel.setForeground(Color.red);
        messageLabel.setVisible(false);
        messageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

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
    }

    public DataProvider getDataProvider() {
        return dataProvider;
    }

    private void initComponents() {
        dataProviderChoice = new JComboBox<>();
        openButton = new JButton();
        cancelButton = new JButton();

        setTitle(rb.getString("NewChartDialog.title"));

        // create the label for the data provider field
        JLabel dataProviderLabel = new JLabel(rb.getString("NewChartDialog.dataProvider"));
        Font bolderFont = dataProviderLabel.getFont().deriveFont(
                Collections.singletonMap(
                        TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD));
        dataProviderLabel.setFont(bolderFont);

        dataProviderChoice.addActionListener(this::onDataProviderChange);

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
        c.gridwidth = 1;
        //pane.add(symbolLabel, c);
        c.weightx = 1.0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        //pane.add(symbolField, c);
        c.weightx = 0.0;
        c.gridwidth = 1;
        pane.add(dataProviderLabel, c);
        c.gridwidth = GridBagConstraints.REMAINDER;
        pane.add(dataProviderChoice, c);
        c.gridwidth = 1;
        //pane.add(exchangeLabel, c);
        c.gridwidth = GridBagConstraints.REMAINDER;
        //pane.add(exchangeChoice, c);
        c.gridwidth = 1;
        //pane.add(templateLabel, c);
        c.gridwidth = GridBagConstraints.REMAINDER;
        //pane.add(templateChoice, c);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(openButton);
        buttons.add(cancelButton);
        pane.add(buttons, c);

        //        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        //		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(esc, "close");
        //		getRootPane().getActionMap().put("close", closeAction);
        // make the TextField to gain focus whenever dialog is activated
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

//    /**
//     * Changes the currently selected symbol and data provider to match the
//     * given {@code ChartOpen} history.
//     * <p>
//     * The method is usually called by the framework after the user changes the
//     * selection in the opened chart history.
//     *
//     * @param p
//     *            the chart open history data
//     */
//    public void select(ChartOpen p) {
//        // change displayed symbol name
//        symbolField.setText(p.getSymbol());
//
//        // change selected data provider
//        for (int index = 0; index < dataProviderChoice.getItemCount(); index++) {
//            DataProvider provider = dataProviderChoice.getItemAt(index);
//            if (p.getProviderName().equals(provider.getName())) {
//                dataProviderChoice.setSelectedIndex(index);
//                break;
//            }
//        }
//    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.width = Math.max(size.width, size.height * 13/10);
        return size;
    }

    protected void onDataProviderChange(ActionEvent evt) {
        messageLabel.setVisible(false);
        messageUrl = "";
        JComboBox<DataProvider> source = (JComboBox<DataProvider>) evt.getSource();
        DataProvider provider = (DataProvider) source.getSelectedItem();
        this.dataProvider = provider;
        if (symbolSelector != null)
            symbolSelector.setDataProvider(provider);
    }

    protected void onAccepted(ActionEvent e) {
        symbolSelector.getAutocompleter().disableTimer();
        dataProvider = (DataProvider) dataProviderChoice.getSelectedItem();
        selectedSymbols = symbolSelector.getSelectedSymbols(AssetTypes.GENERIC)
                .stream()
                .map(symb -> new Symbol(symb, dataProvider))
                .toList();

        setVisible(false);
    }

    protected void onCancelled(ActionEvent e) {
        this.selectedSymbols = List.of();
        this.dataProvider = null;
        setVisible(false);
    }

    private void onMessageClicked(java.awt.event.MouseEvent evt) {
        if (!messageUrl.isEmpty()) {
            try {
                //DesktopUtil.browse(messageUrl);
            } catch (Exception ex) {
            }
        }
    }

    public List<Symbol> getSelectedSymbols() {
        return selectedSymbols;
    }

    private JButton cancelButton;
    private JButton openButton;
    private JComboBox<DataProvider> dataProviderChoice;
    private JLabel messageLabel;
}
