/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.ui.chart.BasicStrokes;
import one.chartsy.ui.chart.ChartPlugin;
import one.chartsy.ui.chart.ChartPlugin.Parameter;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.ui.chart.internal.ChartPluginParameterUtils;
import one.chartsy.ui.chart.properties.NamedPluginNode;
import org.openide.explorer.propertysheet.PropertySheet;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A reusable chooser surface for chart studies that stays renderable off-screen for visual verification.
 *
 * @author Mariusz Bernacki
 */
public class IndicatorChooserPanel extends JPanel {
    private static final String EMPTY_CARD = "empty";
    private static final String PROPERTY_CARD = "properties";
    private static final List<String> INDICATOR_CATEGORY_ORDER = List.of("Momentum", "Volatility", "Market Structure", "Miscellaneous");
    private static final List<String> OVERLAY_CATEGORY_ORDER = List.of("Trend", "Bands", "Volume", "Miscellaneous");
    private static final List<PluginKind> KIND_ORDER = List.of(PluginKind.INDICATOR, PluginKind.OVERLAY);
    private static final Comparator<ChartPlugin<?>> PLUGIN_COMPARATOR = Comparator
            .comparingInt((ChartPlugin<?> plugin) -> PluginKind.from(plugin).sortOrder())
            .thenComparing(ChartPlugin::getName, String.CASE_INSENSITIVE_ORDER);

    private final List<ChartPlugin<?>> availablePlugins = new ArrayList<>();
    private final List<ChartPlugin<?>> selectedPlugins = new ArrayList<>();
    private final DefaultTreeModel availableTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode("root"));
    private final DefaultComboBoxModel<ChartPlugin<?>> pluginSelectorModel = new DefaultComboBoxModel<>();
    private final PlotObjectTableModel plotObjectTableModel = new PlotObjectTableModel();
    private final JTree availableTree = new JTree(availableTreeModel);
    private final JTable plotObjectTable = new JTable(plotObjectTableModel);
    private final JSplitPane topSplit = new JSplitPane();
    private final JSplitPane mainSplit = new JSplitPane();
    private final JTextField filterField = new JTextField();
    private final JButton addButton = new JButton();
    private final JButton removeButton = new JButton();
    private final JButton expandButton = new JButton("+");
    private final JButton collapseButton = new JButton("-");
    private final JComboBox<ChartPlugin<?>> pluginSelector = new JComboBox<>(pluginSelectorModel);
    private final JLabel selectionMetaLabel = new JLabel(" ");
    private final JPanel propertyContentPanel = new JPanel(new java.awt.CardLayout());
    private final PropertySheet propertySheet = new PropertySheet();
    private final JLabel plotObjectCountLabel = new JLabel();

    private boolean synchronizingSelection;
    private boolean splitLayoutInitialized;

    public IndicatorChooserPanel() {
        initComponents();
        registerListeners();
        refreshSelectedPlugins(null);
    }

    public void initForm(Collection<? extends Indicator> allIndicators, Collection<? extends Indicator> selectedIndicators) {
        initForm(allIndicators, selectedIndicators, List.of(), List.of());
    }

    public void initForm(Collection<? extends Indicator> allIndicators,
                         Collection<? extends Indicator> selectedIndicators,
                         Collection<? extends Overlay> allOverlays,
                         Collection<? extends Overlay> selectedOverlays) {
        availablePlugins.clear();
        this.selectedPlugins.clear();

        allIndicators.stream().filter(Objects::nonNull).map(plugin -> (ChartPlugin<?>) plugin).sorted(PLUGIN_COMPARATOR).forEach(availablePlugins::add);
        allOverlays.stream().filter(Objects::nonNull).map(plugin -> (ChartPlugin<?>) plugin).sorted(PLUGIN_COMPARATOR).forEach(availablePlugins::add);
        selectedIndicators.stream().filter(Objects::nonNull).map(plugin -> (ChartPlugin<?>) plugin).map(this::duplicatePluginConfiguration).forEach(selectedPlugins::add);
        selectedOverlays.stream().filter(Objects::nonNull).map(plugin -> (ChartPlugin<?>) plugin).map(this::duplicatePluginConfiguration).forEach(selectedPlugins::add);

        rebuildAvailableTree();
        refreshSelectedPlugins(this.selectedPlugins.isEmpty() ? null : this.selectedPlugins.get(0));
    }

    public ChartPluginSelection getSelection() {
        List<Indicator> indicators = new ArrayList<>();
        List<Overlay> overlays = new ArrayList<>();
        for (ChartPlugin<?> plugin : selectedPlugins) {
            if (plugin instanceof Indicator indicator)
                indicators.add(indicator);
            else if (plugin instanceof Overlay overlay)
                overlays.add(overlay);
        }
        return new ChartPluginSelection(indicators, overlays);
    }

    public List<Indicator> getSelectedIndicators() {
        return getSelection().indicators();
    }

    public List<Overlay> getSelectedOverlays() {
        return getSelection().overlays();
    }

    private void initComponents() {
        setName("indicatorChooser.panel");
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setPreferredSize(new Dimension(1120, 640));

        topSplit.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        topSplit.setLeftComponent(createBrowserPanel());
        topSplit.setRightComponent(createPropertiesPanel());
        topSplit.setBorder(null);
        topSplit.setResizeWeight(0.36);
        topSplit.setContinuousLayout(true);
        topSplit.setName("indicatorChooser.topSplit");

        mainSplit.setOrientation(JSplitPane.VERTICAL_SPLIT);
        mainSplit.setTopComponent(topSplit);
        mainSplit.setBottomComponent(createPlotObjectsPanel());
        mainSplit.setBorder(null);
        mainSplit.setResizeWeight(0.52);
        mainSplit.setContinuousLayout(true);
        mainSplit.setName("indicatorChooser.mainSplit");
        add(mainSplit, BorderLayout.CENTER);
    }

    private JComponent createBrowserPanel() {
        JPanel panel = createSectionPanel();
        panel.setName("indicatorChooser.browserPanel");

        JLabel titleLabel = createSectionTitle("Study Library");
        JPanel toolbar = new JPanel(new GridBagLayout());
        toolbar.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, 6);

        collapseButton.setToolTipText("Collapse all groups");
        collapseButton.setFocusable(false);
        collapseButton.setMargin(new Insets(2, 8, 2, 8));
        gbc.gridx = 0;
        toolbar.add(collapseButton, gbc);

        expandButton.setToolTipText("Expand all groups");
        expandButton.setFocusable(false);
        expandButton.setMargin(new Insets(2, 8, 2, 8));
        gbc.gridx = 1;
        toolbar.add(expandButton, gbc);

        JLabel filterLabel = new JLabel("Filter:");
        gbc.gridx = 2;
        toolbar.add(filterLabel, gbc);

        filterField.setName("indicatorChooser.filterField");
        filterField.setToolTipText("Filter studies by name");
        gbc.gridx = 3;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 0);
        toolbar.add(filterField, gbc);

        JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
        headerPanel.setOpaque(false);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(toolbar, BorderLayout.CENTER);

        availableTree.setName("indicatorChooser.availableTree");
        availableTree.setRootVisible(false);
        availableTree.setShowsRootHandles(true);
        availableTree.setRowHeight(20);
        availableTree.setCellRenderer(new PluginTreeCellRenderer());
        availableTree.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JScrollPane treeScrollPane = new JScrollPane(availableTree);
        treeScrollPane.setBorder(createInnerBorder());

        addButton.setText(NbBundle.getMessage(IndicatorChooserPanel.class, "ChPChooser.btnAdd.text"));
        addButton.setEnabled(false);

        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setOpaque(false);
        footerPanel.add(addButton, BorderLayout.WEST);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(treeScrollPane, BorderLayout.CENTER);
        panel.add(footerPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent createPropertiesPanel() {
        JPanel panel = createSectionPanel();
        panel.setName("indicatorChooser.propertiesPanel");

        JLabel titleLabel = createSectionTitle(NbBundle.getMessage(IndicatorChooserPanel.class, "ChPChooser.lblProperties.text"));
        JPanel selectorPanel = new JPanel(new GridBagLayout());
        selectorPanel.setName("indicatorChooser.selectorPanel");
        selectorPanel.setOpaque(false);
        selectorPanel.setBorder(createInnerBorder());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel selectorLabel = new JLabel("Selection:");
        selectorPanel.add(selectorLabel, gbc);

        pluginSelector.setName("indicatorChooser.selector");
        pluginSelector.setEnabled(false);
        pluginSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                String label = value instanceof ChartPlugin<?> plugin ? plugin.getLabel() : "";
                return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
            }
        });
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 0, 4, 8);
        selectorPanel.add(pluginSelector, gbc);

        selectionMetaLabel.setName("indicatorChooser.selectionMeta");
        selectionMetaLabel.setForeground(resolveSecondaryTextColor());
        selectionMetaLabel.setFont(selectionMetaLabel.getFont().deriveFont(Font.PLAIN,
                Math.max(11.0f, selectionMetaLabel.getFont().getSize2D() - 1.0f)));
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        selectorPanel.add(selectionMetaLabel, gbc);

        propertySheet.setName("indicatorChooser.propertySheet");
        propertySheet.setDescriptionAreaVisible(false);
        propertySheet.setBorder(BorderFactory.createEmptyBorder());

        JPanel propertyCardPanel = new JPanel(new BorderLayout());
        propertyCardPanel.setName("indicatorChooser.propertyCard");
        propertyCardPanel.setBorder(createInnerBorder());
        propertyCardPanel.add(propertySheet, BorderLayout.CENTER);

        JPanel emptyPanel = new JPanel(new BorderLayout());
        emptyPanel.setName("indicatorChooser.emptyState");
        emptyPanel.setBorder(createInnerBorder());
        JLabel emptyLabel = new JLabel("No study selected.");
        emptyLabel.setHorizontalAlignment(SwingConstants.LEFT);
        emptyLabel.setVerticalAlignment(SwingConstants.TOP);
        emptyLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.BOLD, emptyLabel.getFont().getSize2D() + 1.0f));
        emptyPanel.add(emptyLabel, BorderLayout.NORTH);

        propertyContentPanel.setName("indicatorChooser.propertyContent");
        propertyContentPanel.add(emptyPanel, EMPTY_CARD);
        propertyContentPanel.add(propertyCardPanel, PROPERTY_CARD);

        JPanel northPanel = new JPanel(new BorderLayout(0, 4));
        northPanel.setOpaque(false);
        northPanel.add(titleLabel, BorderLayout.NORTH);
        northPanel.add(selectorPanel, BorderLayout.CENTER);

        panel.add(northPanel, BorderLayout.NORTH);
        panel.add(propertyContentPanel, BorderLayout.CENTER);
        return panel;
    }

    private JComponent createPlotObjectsPanel() {
        JPanel panel = createSectionPanel();
        panel.setName("indicatorChooser.plotPanel");

        JLabel titleLabel = createSectionTitle("Plot Objects:");
        plotObjectTable.setName("indicatorChooser.plotTable");
        plotObjectTable.setRowHeight(22);
        plotObjectTable.setFillsViewportHeight(true);
        plotObjectTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        plotObjectTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        plotObjectTable.getTableHeader().setReorderingAllowed(false);
        plotObjectTable.setDefaultRenderer(Object.class, new PlotObjectCellRenderer());
        plotObjectTable.setDefaultRenderer(Boolean.class, new PlotObjectCellRenderer());
        plotObjectTable.setDefaultRenderer(Color.class, new ColorSwatchRenderer());
        plotObjectTable.getColumnModel().getColumn(0).setPreferredWidth(260);
        plotObjectTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        plotObjectTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        plotObjectTable.getColumnModel().getColumn(3).setPreferredWidth(190);
        plotObjectTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        plotObjectTable.getColumnModel().getColumn(5).setPreferredWidth(120);
        plotObjectTable.getColumnModel().getColumn(6).setPreferredWidth(60);
        plotObjectTable.getColumnModel().getColumn(7).setMinWidth(68);
        plotObjectTable.getColumnModel().getColumn(7).setPreferredWidth(76);
        plotObjectTable.getColumnModel().getColumn(7).setMaxWidth(84);

        JScrollPane tableScrollPane = new JScrollPane(plotObjectTable);
        tableScrollPane.setBorder(createInnerBorder());
        tableScrollPane.setPreferredSize(new Dimension(0, 232));

        removeButton.setText(NbBundle.getMessage(IndicatorChooserPanel.class, "ChPChooser.btnRemove.text"));
        removeButton.setEnabled(false);

        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setOpaque(false);
        footerPanel.add(removeButton, BorderLayout.WEST);
        footerPanel.add(plotObjectCountLabel, BorderLayout.EAST);

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(tableScrollPane, BorderLayout.CENTER);
        panel.add(footerPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void registerListeners() {
        addButton.addActionListener(event -> addSelectedPlugin());
        removeButton.addActionListener(event -> removeSelectedPlugin());
        expandButton.addActionListener(event -> expandAll());
        collapseButton.addActionListener(event -> collapseAll());
        availableTree.addTreeSelectionListener(event -> addButton.setEnabled(getSelectedPrototype() != null));
        availableTree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && getSelectedPrototype() != null)
                    addSelectedPlugin();
            }
        });
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuildAvailableTree();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuildAvailableTree();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuildAvailableTree();
            }
        });
        pluginSelector.addActionListener(event -> {
            if (!synchronizingSelection)
                selectPlugin((ChartPlugin<?>) pluginSelector.getSelectedItem(), true);
        });
        plotObjectTable.getSelectionModel().addListSelectionListener(this::onPlotObjectSelectionChanged);
        propertySheet.addPropertyChangeListener(event -> refreshVisualSummary());
    }

    private void onPlotObjectSelectionChanged(ListSelectionEvent event) {
        if (event.getValueIsAdjusting() || synchronizingSelection)
            return;
        int viewRow = plotObjectTable.getSelectedRow();
        if (viewRow < 0)
            return;
        PlotObjectRow row = plotObjectTableModel.getRow(viewRow);
        selectPlugin(row == null ? null : row.plugin(), false);
    }

    private void addSelectedPlugin() {
        ChartPlugin<?> prototype = getSelectedPrototype();
        if (prototype == null)
            return;

        ChartPlugin<?> plugin = instantiatePlugin(prototype);
        if (plugin instanceof Indicator indicator)
            indicator.setPanelId(UUID.randomUUID());
        selectedPlugins.add(plugin);
        refreshSelectedPlugins(plugin);
    }

    private void removeSelectedPlugin() {
        ChartPlugin<?> selected = getCurrentlySelectedPlugin();
        if (selected == null)
            return;

        int selectedIndex = selectedPlugins.indexOf(selected);
        selectedPlugins.remove(selected);
        ChartPlugin<?> nextSelection = selectedPlugins.isEmpty() ? null : selectedPlugins.get(Math.max(0, selectedIndex - 1));
        refreshSelectedPlugins(nextSelection);
    }

    private void refreshSelectedPlugins(ChartPlugin<?> selection) {
        synchronizingSelection = true;
        try {
            pluginSelectorModel.removeAllElements();
            for (ChartPlugin<?> plugin : selectedPlugins)
                pluginSelectorModel.addElement(plugin);

            pluginSelector.setEnabled(!selectedPlugins.isEmpty());
            plotObjectTableModel.setPlugins(selectedPlugins);
            plotObjectCountLabel.setText(selectedPlugins.size() + " study(s), " + plotObjectTableModel.getRowCount() + " visual row(s)");
            refreshVisualSummary();

            ChartPlugin<?> effectiveSelection = selection;
            if (effectiveSelection == null || !selectedPlugins.contains(effectiveSelection))
                effectiveSelection = selectedPlugins.isEmpty() ? null : selectedPlugins.get(0);

            pluginSelectorModel.setSelectedItem(effectiveSelection);
            selectPlugin(effectiveSelection, true);
            ChartPlugin<?> deferredSelection = effectiveSelection;
            SwingUtilities.invokeLater(() -> {
                if (deferredSelection != null && (pluginSelector.getSelectedItem() == null || plotObjectTable.getSelectedRow() < 0))
                    selectPlugin(deferredSelection, true);
            });
        } finally {
            synchronizingSelection = false;
        }
    }

    private void refreshVisualSummary() {
        plotObjectTableModel.refresh();
        plotObjectTable.repaint();
        pluginSelector.repaint();
    }

    private void selectPlugin(ChartPlugin<?> plugin, boolean updateTableSelection) {
        synchronizingSelection = true;
        try {
            pluginSelector.setSelectedItem(plugin);
            removeButton.setEnabled(plugin != null);
            selectionMetaLabel.setText(plugin == null ? " " : describePluginType(plugin) + " - " + describePanelPlacement(plugin));
            if (plugin == null) {
                propertySheet.setNodes(null);
                showPropertyCard(EMPTY_CARD);
                if (updateTableSelection)
                    plotObjectTable.clearSelection();
                return;
            }

            propertySheet.setNodes(new Node[]{createPluginNode(plugin)});
            showPropertyCard(PROPERTY_CARD);
            if (updateTableSelection) {
                int rowIndex = plotObjectTableModel.indexOf(plugin);
                if (rowIndex >= 0) {
                    plotObjectTable.getSelectionModel().setSelectionInterval(rowIndex, rowIndex);
                    Rectangle rowBounds = plotObjectTable.getCellRect(rowIndex, 0, true);
                    plotObjectTable.scrollRectToVisible(rowBounds);
                }
            }
        } finally {
            synchronizingSelection = false;
        }
    }

    private void showPropertyCard(String cardName) {
        java.awt.CardLayout layout = (java.awt.CardLayout) propertyContentPanel.getLayout();
        layout.show(propertyContentPanel, cardName);
    }

    private ChartPlugin<?> getCurrentlySelectedPlugin() {
        Object selectedItem = pluginSelector.getSelectedItem();
        if (selectedItem instanceof ChartPlugin<?> plugin)
            return plugin;

        int viewRow = plotObjectTable.getSelectedRow();
        PlotObjectRow row = viewRow >= 0 ? plotObjectTableModel.getRow(viewRow) : null;
        return row == null ? null : row.plugin();
    }

    private void rebuildAvailableTree() {
        String filter = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.ROOT);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        Map<PluginKind, DefaultMutableTreeNode> kindNodes = new LinkedHashMap<>();
        Map<PluginKind, Map<String, DefaultMutableTreeNode>> categoryNodes = new LinkedHashMap<>();
        for (PluginKind kind : KIND_ORDER) {
            kindNodes.put(kind, new DefaultMutableTreeNode(kind.groupLabel()));
            categoryNodes.put(kind, new LinkedHashMap<>());
        }

        availablePlugins.stream()
                .filter(plugin -> filter.isEmpty() || plugin.getName().toLowerCase(Locale.ROOT).contains(filter))
                .sorted(PLUGIN_COMPARATOR)
                .forEach(plugin -> {
                    PluginKind kind = PluginKind.from(plugin);
                    String category = resolveCategory(plugin);
                    DefaultMutableTreeNode categoryNode = categoryNodes.get(kind).computeIfAbsent(category, DefaultMutableTreeNode::new);
                    categoryNode.add(new DefaultMutableTreeNode(plugin, false));
                });

        for (PluginKind kind : KIND_ORDER) {
            DefaultMutableTreeNode kindNode = kindNodes.get(kind);
            for (DefaultMutableTreeNode categoryNode : orderedCategoryNodes(kind, categoryNodes.get(kind))) {
                if (categoryNode.getChildCount() > 0)
                    kindNode.add(categoryNode);
            }
            if (kindNode.getChildCount() > 0)
                root.add(kindNode);
        }

        availableTreeModel.setRoot(root);
        addButton.setEnabled(false);
        if (root.getChildCount() > 0) {
            expandAll();
            selectFirstLeaf(root);
        }
    }

    private List<DefaultMutableTreeNode> orderedCategoryNodes(PluginKind kind, Map<String, DefaultMutableTreeNode> nodes) {
        List<DefaultMutableTreeNode> orderedNodes = new ArrayList<>();
        Map<String, DefaultMutableTreeNode> remaining = new LinkedHashMap<>(nodes);
        for (String category : preferredCategoryOrder(kind)) {
            DefaultMutableTreeNode node = remaining.remove(category);
            if (node != null)
                orderedNodes.add(node);
        }
        remaining.entrySet().stream().sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)).map(Map.Entry::getValue).forEach(orderedNodes::add);
        return orderedNodes;
    }

    private List<String> preferredCategoryOrder(PluginKind kind) {
        return switch (kind) {
            case INDICATOR -> INDICATOR_CATEGORY_ORDER;
            case OVERLAY -> OVERLAY_CATEGORY_ORDER;
        };
    }

    private void selectFirstLeaf(DefaultMutableTreeNode root) {
        Enumeration<?> depthFirst = root.depthFirstEnumeration();
        while (depthFirst.hasMoreElements()) {
            Object next = depthFirst.nextElement();
            if (next instanceof DefaultMutableTreeNode node && node.isLeaf() && node.getUserObject() instanceof ChartPlugin<?>) {
                TreePath path = new TreePath(node.getPath());
                availableTree.setSelectionPath(path);
                availableTree.scrollPathToVisible(path);
                addButton.setEnabled(true);
                return;
            }
        }
    }

    private ChartPlugin<?> getSelectedPrototype() {
        TreePath selectionPath = availableTree.getSelectionPath();
        if (selectionPath == null)
            return null;

        Object node = selectionPath.getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode treeNode && treeNode.getUserObject() instanceof ChartPlugin<?> plugin)
            return plugin;
        return null;
    }

    private void expandAll() {
        for (int row = 0; row < availableTree.getRowCount(); row++)
            availableTree.expandRow(row);
    }

    private void collapseAll() {
        for (int row = availableTree.getRowCount() - 1; row > 0; row--)
            availableTree.collapseRow(row);
    }


    private String resolveCategory(ChartPlugin<?> plugin) {
        return switch (PluginKind.from(plugin)) {
            case INDICATOR -> resolveIndicatorCategory((Indicator) plugin);
            case OVERLAY -> resolveOverlayCategory((Overlay) plugin);
        };
    }

    private String resolveIndicatorCategory(Indicator indicator) {
        return switch (indicator.getName()) {
            case "Chande Momentum Oscillator", "Continuation Index", "Return/Volume Correlation", "Ultimate Strength Index" -> "Momentum";
            case "Fractal Dimension", "Median Range", "Range Compression Score", "Range Inversion Sigma", "Sfora, Width" -> "Volatility";
            case "Haar Breakout Distance", "Liquidity", "Quota" -> "Market Structure";
            default -> "Miscellaneous";
        };
    }

    private String resolveOverlayCategory(Overlay overlay) {
        return switch (overlay.getName()) {
            case "FRAMA, Leading", "FRAMA, Trailing" -> "Trend";
            case "Sfora", "Sentiment Bands" -> "Bands";
            case "Volume" -> "Volume";
            default -> "Miscellaneous";
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ChartPlugin<?> instantiatePlugin(ChartPlugin<?> prototype) {
        return (ChartPlugin<?>) ((ChartPlugin) prototype).newInstance();
    }

    private ChartPlugin<?> duplicatePluginConfiguration(ChartPlugin<?> source) {
        ChartPlugin<?> copy = instantiatePlugin(source);
        ChartPluginParameterUtils.copyParameterValues(source, copy);
        return copy;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Node createPluginNode(ChartPlugin<?> plugin) {
        return new NamedPluginNode((ChartPlugin) plugin);
    }

    private JPanel createSectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        return panel;
    }

    private JLabel createSectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() + 1.0f));
        return label;
    }

    @Override
    public void doLayout() {
        super.doLayout();
        if (!splitLayoutInitialized && getWidth() > 0 && getHeight() > 0) {
            applyInitialDividerLocations();
            splitLayoutInitialized = true;
        }
    }

    private void applyInitialDividerLocations() {
        int topWidth = topSplit.getWidth();
        if (topWidth > 0) {
            int desiredLeftWidth = Math.max(340, Math.min((int) Math.round(topWidth * 0.37), topWidth - 520));
            topSplit.setDividerLocation(desiredLeftWidth);
        }

        int mainHeight = mainSplit.getHeight();
        if (mainHeight > 0) {
            int desiredTopHeight = Math.max(310, Math.min((int) Math.round(mainHeight * 0.52), mainHeight - 240));
            mainSplit.setDividerLocation(desiredTopHeight);
        }
    }

    private Border createInnerBorder() {
        Color borderColor = UIManager.getColor("Separator.foreground");
        if (borderColor == null)
            borderColor = new Color(0xC8CDD3);
        return BorderFactory.createLineBorder(borderColor);
    }

    private Color resolveSecondaryTextColor() {
        Color color = UIManager.getColor("Label.disabledForeground");
        return color != null ? color : new Color(0x5F6773);
    }


    private static List<PlotObjectRow> describePlotObjects(ChartPlugin<?> plugin) {
        Map<String, VisualDescriptor> descriptors = new LinkedHashMap<>();
        Map<String, Boolean> visibilityDescriptors = new LinkedHashMap<>();

        for (Field field : ChartPluginParameterUtils.getParameterFields(plugin.getClass())) {
            Parameter parameter = field.getAnnotation(Parameter.class);
            if (parameter == null)
                continue;

            Object value = ChartPluginParameterUtils.readFieldValue(plugin, field);
            String parameterName = parameter.name();
            if (value instanceof Color color) {
                descriptors.computeIfAbsent(elementNameForColor(parameterName), ignored -> new VisualDescriptor()).color = color;
            } else if (value instanceof Stroke stroke) {
                descriptors.computeIfAbsent(elementNameForStroke(parameterName), ignored -> new VisualDescriptor()).stroke = stroke;
            } else if (value instanceof Boolean visible) {
                visibilityDescriptors.put(elementNameForVisibility(parameterName), visible);
            }
        }

        List<PlotObjectRow> rows = new ArrayList<>();
        Set<String> consumedVisibilityKeys = new LinkedHashSet<>();
        Boolean singleVisibility = visibilityDescriptors.size() == 1 ? visibilityDescriptors.values().iterator().next() : null;
        boolean firstRow = true;
        for (Map.Entry<String, VisualDescriptor> entry : descriptors.entrySet()) {
            String elementName = entry.getKey();
            Boolean visible = resolveVisibility(elementName, visibilityDescriptors, singleVisibility, consumedVisibilityKeys);
            rows.add(new PlotObjectRow(plugin, firstRow, plugin.getLabel(), describePluginType(plugin), describePanelPlacement(plugin),
                    elementName, visible, entry.getValue().stroke, entry.getValue().color));
            firstRow = false;
        }

        for (Map.Entry<String, Boolean> entry : visibilityDescriptors.entrySet()) {
            if (consumedVisibilityKeys.contains(entry.getKey()))
                continue;
            rows.add(new PlotObjectRow(plugin, firstRow, plugin.getLabel(), describePluginType(plugin), describePanelPlacement(plugin),
                    entry.getKey(), entry.getValue(), null, null));
            firstRow = false;
        }

        if (rows.isEmpty()) {
            rows.add(new PlotObjectRow(plugin, true, plugin.getLabel(), describePluginType(plugin), describePanelPlacement(plugin),
                    "Result", null, null, null));
        }
        return rows;
    }

    private static String elementNameForColor(String parameterName) {
        return normalizeElementName(parameterName, "Color");
    }

    private static String elementNameForStroke(String parameterName) {
        return normalizeElementName(parameterName, "Style", "Stroke", "Pen Style");
    }

    private static String elementNameForVisibility(String parameterName) {
        return normalizeElementName(parameterName, "Visibility", "Visible");
    }

    private static String normalizeElementName(String parameterName, String... suffixes) {
        String trimmedName = parameterName == null ? "" : parameterName.trim();
        for (String suffix : suffixes) {
            if (trimmedName.equalsIgnoreCase(suffix))
                return "Result";
            String token = " " + suffix;
            if (trimmedName.length() > token.length() && trimmedName.regionMatches(true, trimmedName.length() - token.length(), token, 0, token.length()))
                return trimmedName.substring(0, trimmedName.length() - token.length()).trim();
        }
        return trimmedName.isBlank() ? "Result" : trimmedName;
    }

    private static Boolean resolveVisibility(String elementName, Map<String, Boolean> visibilityDescriptors, Boolean singleVisibility,
                                             Set<String> consumedVisibilityKeys) {
        String normalizedElement = elementName.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Boolean> entry : visibilityDescriptors.entrySet()) {
            String normalizedVisibility = entry.getKey().toLowerCase(Locale.ROOT);
            if (!normalizedVisibility.isEmpty() && (normalizedElement.equals(normalizedVisibility) || normalizedElement.startsWith(normalizedVisibility))) {
                consumedVisibilityKeys.add(entry.getKey());
                return entry.getValue();
            }
        }
        return singleVisibility;
    }


    private static String describePluginType(ChartPlugin<?> plugin) {
        return PluginKind.from(plugin).displayLabel();
    }

    private static String describePanelPlacement(ChartPlugin<?> plugin) {
        return PluginKind.from(plugin).placementLabel();
    }

    private static String describeStroke(Stroke stroke) {
        return BasicStrokes.getStrokeName(stroke)
                .map(name -> name.replace('_', ' ').toLowerCase(Locale.ROOT))
                .map(IndicatorChooserPanel::capitalizeWords)
                .orElse("");
    }

    private static String describeWidth(Stroke stroke) {
        if (stroke instanceof BasicStroke basicStroke) {
            float width = basicStroke.getLineWidth();
            if (Math.abs(width - Math.round(width)) < 0.001f)
                return Integer.toString(Math.round(width));
            return new DecimalFormat("0.#").format(width);
        }
        return "";
    }

    private static String capitalizeWords(String text) {
        String[] parts = text.split(" ");
        StringBuilder builder = new StringBuilder(text.length());
        for (String part : parts) {
            if (part.isEmpty())
                continue;
            if (builder.length() > 0)
                builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private static final class VisualDescriptor {
        private Color color;
        private Stroke stroke;
    }

    private record PlotObjectRow(ChartPlugin<?> plugin, boolean firstRow, String pluginLabel, String pluginType,
                                 String placement, String elementLabel, Boolean visible, Stroke stroke, Color color) {
    }

    private final class PlotObjectTableModel extends AbstractTableModel {
        private final List<ChartPlugin<?>> plugins = new ArrayList<>();
        private final List<PlotObjectRow> rows = new ArrayList<>();
        private final String[] columnNames = {"Study", "Type", "Panel", "Element", "Visible", "Style", "Width", "Color"};

        void setPlugins(List<ChartPlugin<?>> plugins) {
            this.plugins.clear();
            this.plugins.addAll(plugins);
            refresh();
        }

        void refresh() {
            rows.clear();
            plugins.forEach(plugin -> rows.addAll(describePlotObjects(plugin)));
            fireTableDataChanged();
        }

        PlotObjectRow getRow(int rowIndex) {
            return rowIndex >= 0 && rowIndex < rows.size() ? rows.get(rowIndex) : null;
        }

        int indexOf(ChartPlugin<?> plugin) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).plugin() == plugin)
                    return i;
            }
            return -1;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 4 -> Boolean.class;
                case 7 -> Color.class;
                default -> Object.class;
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PlotObjectRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.firstRow() ? row.pluginLabel() : "";
                case 1 -> row.firstRow() ? row.pluginType() : "";
                case 2 -> row.firstRow() ? row.placement() : "";
                case 3 -> row.elementLabel();
                case 4 -> row.visible();
                case 5 -> describeStroke(row.stroke());
                case 6 -> describeWidth(row.stroke());
                case 7 -> row.color();
                default -> "";
            };
        }
    }

    private static final class PluginTreeCellRenderer extends DefaultTreeCellRenderer {
        private final Border leafBorder = BorderFactory.createEmptyBorder(2, 2, 2, 2);
        private final Border groupBorder = BorderFactory.createEmptyBorder(4, 2, 4, 2);

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode node) {
                Object userObject = node.getUserObject();
                if (userObject instanceof ChartPlugin<?> plugin) {
                    setText(plugin.getName());
                    setToolTipText(describePluginType(plugin) + " - " + describePanelPlacement(plugin));
                    setFont(tree.getFont());
                    setBorder(leafBorder);
                } else {
                    setText(String.valueOf(userObject));
                    setToolTipText(null);
                    setFont(tree.getFont().deriveFont(Font.BOLD));
                    setBorder(groupBorder);
                }
            }
            return this;
        }
    }

    private static class PlotObjectCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(column == 4 || column == 6 ? SwingConstants.CENTER : SwingConstants.LEFT);
            if (value instanceof Boolean visible)
                setText(visible ? "Yes" : "No");
            if (column == 0 && value instanceof String text && !text.isBlank())
                setFont(getFont().deriveFont(Font.BOLD));
            else
                setFont(table.getFont());
            return this;
        }
    }

    private static final class ColorSwatchRenderer extends JPanel implements TableCellRenderer {
        private Color color;
        private boolean selected;

        private ColorSwatchRenderer() {
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            color = value instanceof Color swatch ? swatch : null;
            selected = isSelected;
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (color == null)
                return;

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = Math.max(24, getWidth() - 18);
                int height = 12;
                int x = 8;
                int y = (getHeight() - height) / 2;
                g2.setColor(color);
                g2.fillRoundRect(x, y, width, height, 8, 8);
                g2.setColor(selected ? getForeground() : new Color(0x7A7A7A));
                g2.drawRoundRect(x, y, width, height, 8, 8);
            } finally {
                g2.dispose();
            }
        }
    }

    private enum PluginKind {
        INDICATOR("Indicators", "Indicator", "Separate panel", 0),
        OVERLAY("Overlays", "Overlay", "Main chart", 1);

        private final String groupLabel;
        private final String displayLabel;
        private final String placementLabel;
        private final int sortOrder;

        PluginKind(String groupLabel, String displayLabel, String placementLabel, int sortOrder) {
            this.groupLabel = groupLabel;
            this.displayLabel = displayLabel;
            this.placementLabel = placementLabel;
            this.sortOrder = sortOrder;
        }

        static PluginKind from(ChartPlugin<?> plugin) {
            return plugin instanceof Overlay ? OVERLAY : INDICATOR;
        }

        String groupLabel() {
            return groupLabel;
        }

        String displayLabel() {
            return displayLabel;
        }

        String placementLabel() {
            return placementLabel;
        }

        int sortOrder() {
            return sortOrder;
        }
    }
}

