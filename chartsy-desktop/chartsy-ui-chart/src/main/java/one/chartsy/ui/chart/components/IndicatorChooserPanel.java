/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.ui.chart.ChartPlugin;
import one.chartsy.ui.chart.ChartPluginPlotSource;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.ui.chart.StudyBackedChartPlugin;
import one.chartsy.ui.chart.internal.ChartPluginParameter;
import one.chartsy.ui.chart.internal.ChartPluginParameterUtils;
import one.chartsy.ui.chart.internal.IndicatorPaneSupport;
import one.chartsy.ui.chart.properties.NamedPluginNode;
import one.chartsy.study.StudyPlacement;
import org.openide.explorer.propertysheet.PropertySheet;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
    private final JTree availableTree = new JTree(availableTreeModel);
    private final PlotObjectTreeTable plotObjectTable = new PlotObjectTreeTable();
    private final JSplitPane topSplit = new JSplitPane();
    private final JSplitPane mainSplit = new JSplitPane();
    private final JTextField filterField = new JTextField();
    private final JButton addButton = new JButton();
    private final JButton removeButton = new JButton();
    private final JButton expandButton = new JButton("+");
    private final JButton collapseButton = new JButton("-");
    private final JComboBox<ChartPlugin<?>> pluginSelector = new JComboBox<>(pluginSelectorModel);
    private final DefaultComboBoxModel<PaneChoice> paneSelectorModel = new DefaultComboBoxModel<>();
    private final JComboBox<PaneChoice> paneSelector = new JComboBox<>(paneSelectorModel);
    private final JCheckBox forceCombineCheckBox = new JCheckBox("Force combine");
    private final JLabel paneAssignmentLabel = new JLabel("Pane:");
    private final JLabel selectionMetaLabel = new JLabel(" ");
    private final JPanel propertyContentPanel = new JPanel(new CardLayout());
    private final PropertySheet propertySheet = new PropertySheet();
    private final JLabel plotObjectCountLabel = new JLabel();

    private boolean synchronizingSelection;
    private boolean synchronizingPaneControls;
    private boolean splitLayoutInitialized;
    private ChartPlugin<?> paneSelectionOwner;

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
            ChartPlugin<?> snapshot = duplicatePluginConfiguration(plugin);
            if (snapshot instanceof Indicator indicator)
                indicators.add(indicator);
            else if (snapshot instanceof Overlay overlay)
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
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
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

        paneAssignmentLabel.setName("indicatorChooser.paneLabel");
        paneAssignmentLabel.setVisible(false);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 8, 4, 8);
        selectorPanel.add(paneAssignmentLabel, gbc);

        paneSelector.setName("indicatorChooser.paneSelector");
        paneSelector.setEnabled(false);
        paneSelector.setVisible(false);
        paneSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                String label = value instanceof PaneChoice paneChoice ? paneChoice.label() : "";
                return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
            }
        });
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 4, 8);
        selectorPanel.add(paneSelector, gbc);

        forceCombineCheckBox.setName("indicatorChooser.forceCombine");
        forceCombineCheckBox.setOpaque(false);
        forceCombineCheckBox.setVisible(false);
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;
        selectorPanel.add(forceCombineCheckBox, gbc);

        selectionMetaLabel.setName("indicatorChooser.selectionMeta");
        selectionMetaLabel.setForeground(resolveSecondaryTextColor());
        selectionMetaLabel.setFont(selectionMetaLabel.getFont().deriveFont(Font.PLAIN,
                Math.max(11.0f, selectionMetaLabel.getFont().getSize2D() - 1.0f)));
        gbc.gridy = 3;
        gbc.gridx = 1;
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
        availableTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
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
        paneSelector.addActionListener(event -> applyPaneAssignment());
        forceCombineCheckBox.addActionListener(event -> refreshPaneAssignmentControls(getCurrentlySelectedPlugin()));
        plotObjectTable.getSelectionModel().addListSelectionListener(this::onPlotObjectSelectionChanged);
        plotObjectTable.addVisibilityChangeListener(event -> propertySheet.repaint());
        propertySheet.addPropertyChangeListener(event -> refreshVisualSummary());
    }

    private void onPlotObjectSelectionChanged(ListSelectionEvent event) {
        if (event.getValueIsAdjusting() || synchronizingSelection)
            return;
        int viewRow = plotObjectTable.getSelectedRow();
        if (viewRow < 0)
            return;
        selectPlugin(plotObjectTable.getPluginAt(viewRow), false);
    }

    private void addSelectedPlugin() {
        ChartPlugin<?> prototype = getSelectedPrototype();
        if (prototype == null)
            return;

        ChartPlugin<?> plugin = instantiatePlugin(prototype);
        assignPaneIdIfNeeded(plugin);
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
        normalizeSelectedPaneIds();
        synchronizingSelection = true;
        try {
            pluginSelectorModel.removeAllElements();
            for (ChartPlugin<?> plugin : selectedPlugins)
                pluginSelectorModel.addElement(plugin);

            pluginSelector.setEnabled(!selectedPlugins.isEmpty());
            plotObjectTable.setStudies(describeSelectedPlotObjects());
            plotObjectCountLabel.setText(selectedPlugins.size() + " study(s), "
                    + plotObjectTable.getPlotObjectCount() + " plot object(s)");

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
        ChartPlugin<?> selectedPlugin = getCurrentlySelectedPlugin();
        plotObjectTable.refreshStudies(describeSelectedPlotObjects());
        plotObjectTable.selectPlugin(selectedPlugin);
        pluginSelector.repaint();
        refreshPaneAssignmentControls(getCurrentlySelectedPlugin());
    }

    private void selectPlugin(ChartPlugin<?> plugin, boolean updateTableSelection) {
        synchronizingSelection = true;
        try {
            pluginSelector.setSelectedItem(plugin);
            removeButton.setEnabled(plugin != null);
            selectionMetaLabel.setText(plugin == null ? " " : describePluginType(plugin) + " - " + describePanelPlacement(plugin));
            refreshPaneAssignmentControls(plugin);
            if (plugin == null) {
                propertySheet.setNodes(null);
                showPropertyCard(EMPTY_CARD);
                if (updateTableSelection)
                    plotObjectTable.clearSelection();
                return;
            }

            propertySheet.setNodes(new Node[]{createPluginNode(plugin)});
            showPropertyCard(PROPERTY_CARD);
            if (updateTableSelection)
                plotObjectTable.selectPlugin(plugin);
        } finally {
            synchronizingSelection = false;
        }
    }

    private void showPropertyCard(String cardName) {
        CardLayout layout = (CardLayout) propertyContentPanel.getLayout();
        layout.show(propertyContentPanel, cardName);
    }

    private ChartPlugin<?> getCurrentlySelectedPlugin() {
        Object selectedItem = pluginSelector.getSelectedItem();
        if (selectedItem instanceof ChartPlugin<?> plugin)
            return plugin;

        int viewRow = plotObjectTable.getSelectedRow();
        return viewRow >= 0 ? plotObjectTable.getPluginAt(viewRow) : null;
    }

    private void refreshPaneAssignmentControls(ChartPlugin<?> plugin) {
        if (!(plugin instanceof Indicator indicator) || IndicatorPaneSupport.isMainPanelIndicator(indicator)) {
            hidePaneAssignmentControls(plugin);
            return;
        }

        boolean currentIncompatible = isForcedCombination(indicator);
        if (paneSelectionOwner != plugin) {
            paneSelectionOwner = plugin;
            forceCombineCheckBox.setSelected(currentIncompatible);
        }

        List<PaneCandidate> candidates = paneCandidates(indicator);
        boolean forceCombine = forceCombineCheckBox.isSelected();

        synchronizingPaneControls = true;
        try {
            paneAssignmentLabel.setVisible(true);
            paneSelector.setVisible(true);
            paneSelector.setEnabled(true);
            forceCombineCheckBox.setVisible(!candidates.isEmpty());
            paneSelectorModel.removeAllElements();

            PaneChoice selectedChoice = PaneChoice.newPaneChoice();
            paneSelectorModel.addElement(selectedChoice);

            int paneNumber = 1;
            for (PaneCandidate candidate : candidates) {
                boolean isCurrent = candidate.group().id() == indicator.getPanelId();
                boolean compatible = candidate.compatibility() == IndicatorPaneSupport.Compatibility.COMPATIBLE;
                if (!compatible && !forceCombine && !isCurrent)
                    continue;

                PaneChoice choice = new PaneChoice(
                        candidate.group().id(),
                        formatPaneChoice(candidate.group(), paneNumber++, compatible, isCurrent),
                        false
                );
                paneSelectorModel.addElement(choice);
                if (isCurrent)
                    selectedChoice = choice;
            }

            paneSelector.setSelectedItem(selectedChoice);
        } finally {
            synchronizingPaneControls = false;
        }
    }

    private void applyPaneAssignment() {
        if (synchronizingPaneControls)
            return;

        ChartPlugin<?> plugin = getCurrentlySelectedPlugin();
        if (!(plugin instanceof Indicator indicator) || IndicatorPaneSupport.isMainPanelIndicator(indicator))
            return;

        PaneChoice choice = (PaneChoice) paneSelector.getSelectedItem();
        if (choice == null)
            return;

        int panelId = selectedPaneId(choice);
        if (panelId != indicator.getPanelId()) {
            indicator.setPanelId(panelId);
            refreshSelectedPlugins(indicator);
        }
    }

    private boolean isForcedCombination(Indicator indicator) {
        return paneCandidates(indicator).stream()
                .filter(candidate -> candidate.group().id() == indicator.getPanelId())
                .findFirst()
                .map(candidate -> candidate.compatibility() == IndicatorPaneSupport.Compatibility.INCOMPATIBLE)
                .orElse(false);
    }

    private List<PaneCandidate> paneCandidates(Indicator indicator) {
        List<PaneCandidate> candidates = new ArrayList<>();
        for (IndicatorPaneSupport.PaneGroup group : selectedPaneGroups()) {
            List<Indicator> occupants = group.indicators().stream()
                    .filter(candidate -> candidate != indicator)
                    .toList();
            if (occupants.isEmpty())
                continue;
            candidates.add(new PaneCandidate(group.withIndicators(occupants), IndicatorPaneSupport.compatibility(indicator, occupants)));
        }
        return candidates;
    }

    private String formatPaneChoice(IndicatorPaneSupport.PaneGroup group, int paneNumber, boolean compatible, boolean current) {
        StringBuilder label = new StringBuilder("Pane ").append(paneNumber);
        if (current)
            label.append(" (Current)");
        else if (!compatible)
            label.append(" (Force combine)");
        label.append(" - ").append(group.indicators().stream()
                .map(ChartPlugin::getLabel)
                .distinct()
                .limit(3)
                .reduce((left, right) -> left + ", " + right)
                .orElse("Empty"));
        return label.toString();
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
        if (plugin instanceof StudyBackedChartPlugin studyPlugin)
            return studyPlugin.getStudyDescriptor().category();

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

    private void assignPaneIdIfNeeded(ChartPlugin<?> plugin) {
        if (plugin instanceof Indicator indicator && IndicatorPaneSupport.isOwnPanelIndicator(indicator))
            indicator.setPanelId(nextSelectedPaneId());
    }

    private void normalizeSelectedPaneIds() {
        IndicatorPaneSupport.normalizePaneIds(selectedOwnPanelIndicators());
    }

    private List<Indicator> selectedOwnPanelIndicators() {
        return selectedPlugins.stream()
                .filter(Indicator.class::isInstance)
                .map(Indicator.class::cast)
                .filter(IndicatorPaneSupport::isOwnPanelIndicator)
                .toList();
    }

    private List<IndicatorPaneSupport.PaneGroup> selectedPaneGroups() {
        return IndicatorPaneSupport.groupByPane(selectedOwnPanelIndicators());
    }

    private int nextSelectedPaneId() {
        return IndicatorPaneSupport.nextPanelId(selectedOwnPanelIndicators());
    }

    private int selectedPaneId(PaneChoice choice) {
        return choice.newPane() ? nextSelectedPaneId() : choice.panelId();
    }

    private void hidePaneAssignmentControls(ChartPlugin<?> plugin) {
        paneSelectionOwner = plugin;
        synchronizingPaneControls = true;
        try {
            paneAssignmentLabel.setVisible(false);
            paneSelector.setVisible(false);
            paneSelector.setEnabled(false);
            forceCombineCheckBox.setVisible(false);
            paneSelectorModel.removeAllElements();
        } finally {
            synchronizingPaneControls = false;
        }
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

    private List<PlotObjectTreeTable.Study> describeSelectedPlotObjects() {
        return selectedPlugins.stream()
                .map(plugin -> new PlotObjectTreeTable.Study(
                        plugin,
                        describePluginType(plugin),
                        describePanelPlacement(plugin),
                        describePlotObjects(plugin)))
                .toList();
    }

    private List<PlotObjectTreeTable.Plot> describePlotObjects(ChartPlugin<?> plugin) {
        if (plugin instanceof ChartPluginPlotSource plotSource)
            return describeDeclaredPlotObjects(plugin, plotSource);
        return List.of();
    }

    private List<PlotObjectTreeTable.Plot> describeDeclaredPlotObjects(
            ChartPlugin<?> plugin, ChartPluginPlotSource plotSource) {
        Map<String, ChartPluginParameter> parametersById = parametersById(plugin);
        return plotSource.getPlotDescriptors().stream()
                .map(plot -> PlotObjectTreeTable.Plot.withVisibility(
                        plot.label(),
                        parameterValue(parametersById, plot.strokeParameterId(), Stroke.class),
                        parameterValue(parametersById, plot.colorParameterId(), Color.class),
                        parametersById.get(plot.visibilityParameterId())))
                .toList();
    }

    private static Map<String, ChartPluginParameter> parametersById(ChartPlugin<?> plugin) {
        Map<String, ChartPluginParameter> parameters = new LinkedHashMap<>();
        for (ChartPluginParameter parameter : ChartPluginParameterUtils.getParameters(plugin))
            parameters.put(parameter.id(), parameter);
        return parameters;
    }

    private static <T> T parameterValue(
            Map<String, ChartPluginParameter> parameters,
            String parameterId,
            Class<T> valueType) {
        if (parameterId == null || parameterId.isBlank())
            return null;
        ChartPluginParameter parameter = parameters.get(parameterId);
        Object value = parameter == null ? null : parameter.getValue();
        return valueType.isInstance(value) ? valueType.cast(value) : null;
    }

    private static String describePluginType(ChartPlugin<?> plugin) {
        return PluginKind.from(plugin).displayLabel();
    }

    private static String describeAvailablePlacement(ChartPlugin<?> plugin) {
        if (plugin instanceof StudyBackedChartPlugin studyPlugin
                && studyPlugin.getStudyDescriptor().placement() == StudyPlacement.MAIN_PANEL)
            return "Main Panel";
        return PluginKind.from(plugin).placementLabel();
    }

    private String describePanelPlacement(ChartPlugin<?> plugin) {
        if (plugin instanceof StudyBackedChartPlugin studyPlugin) {
            if (studyPlugin.getStudyDescriptor().placement() == StudyPlacement.MAIN_PANEL)
                return "Main Panel";
        }
        if (plugin instanceof Indicator indicator && IndicatorPaneSupport.isOwnPanelIndicator(indicator)) {
            List<IndicatorPaneSupport.PaneGroup> panes = selectedPaneGroups();
            for (int i = 0; i < panes.size(); i++) {
                if (panes.get(i).id() == indicator.getPanelId())
                    return "Pane " + (i + 1);
            }
            return "Pane";
        }
        return PluginKind.from(plugin).placementLabel();
    }

    private record PaneCandidate(IndicatorPaneSupport.PaneGroup group, IndicatorPaneSupport.Compatibility compatibility) {
    }

    private record PaneChoice(int panelId, String label, boolean newPane) {
        static PaneChoice newPaneChoice() {
            return new PaneChoice(0, "New Pane", true);
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
                    setToolTipText(describePluginType(plugin) + " - " + describeAvailablePlacement(plugin));
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
