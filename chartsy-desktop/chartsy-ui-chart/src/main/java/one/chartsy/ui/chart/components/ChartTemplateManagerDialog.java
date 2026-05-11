/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.ui.chart.ChartTemplate;
import one.chartsy.ui.chart.ChartTemplateCatalog;
import one.chartsy.ui.chart.ChartTemplateSummary;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.IndicatorManager;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.ui.chart.OverlayManager;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.windows.WindowManager;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.Serial;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ChartTemplateManagerDialog extends JDialog {
    @Serial
    private static final long serialVersionUID = -3829067343343922252L;

    private final ChartTemplateCatalog templateCatalog;
    private final Collection<Indicator> allIndicators;
    private final Collection<Overlay> allOverlays;
    private final DefaultListModel<ChartTemplateSummary> templateListModel = new DefaultListModel<>();
    private final JList<ChartTemplateSummary> templateList = new JList<>(templateListModel);
    private final IndicatorChooserPanel chooserPanel = new IndicatorChooserPanel();
    private final JButton newButton = new JButton("New");
    private final JButton duplicateButton = new JButton("Duplicate");
    private final JButton moreActionsButton = new JButton("More");
    private final JButton saveButton = new JButton("Save");
    private final JButton closeButton = new JButton("Close");

    private ChartTemplateSummary selectedTemplate;
    private boolean selectedTemplateLoaded;
    private ChartTemplate loadedChartTemplate;

    public ChartTemplateManagerDialog(Component locationAnchor, UUID preferredTemplateKey) {
        this(locationAnchor, preferredTemplateKey,
                ChartTemplateCatalog.getDefault(),
                IndicatorManager.getDefault().getIndicatorsList(),
                OverlayManager.getDefault().getOverlaysList());
    }

    ChartTemplateManagerDialog(Component locationAnchor,
                               UUID preferredTemplateKey,
                               ChartTemplateCatalog templateCatalog,
                               Collection<Indicator> allIndicators,
                               Collection<Overlay> allOverlays) {
        super(resolveOwner(locationAnchor));
        this.templateCatalog = Objects.requireNonNull(templateCatalog, "templateCatalog");
        this.allIndicators = Objects.requireNonNull(allIndicators, "allIndicators");
        this.allOverlays = Objects.requireNonNull(allOverlays, "allOverlays");
        setTitle("Manage Templates");
        setModal(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        initComponents();
        registerKeyboardActions();
        refreshTemplates(preferredTemplateKey);
        setMinimumSize(new Dimension(1280, 760));
        pack();
    }

    public ChartTemplateSummary getSelectedTemplate() {
        return selectedTemplate;
    }

    private void initComponents() {
        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String label = "";
                if (value instanceof ChartTemplateSummary template) {
                    label = template.name();
                    if (template.defaultTemplate())
                        label += " (Default)";
                    if (template.builtIn())
                        label += " [Built-in]";
                }
                return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
            }
        });
        templateList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting())
                loadSelectedTemplate();
        });

        newButton.addActionListener(this::onNewTemplate);
        duplicateButton.addActionListener(this::onDuplicateTemplate);
        moreActionsButton.addActionListener(this::onMoreActions);
        saveButton.addActionListener(this::onSaveTemplate);
        closeButton.addActionListener(__ -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));

        configureHeaderButton(newButton, true);
        configureHeaderButton(duplicateButton, false);
        configureHeaderButton(moreActionsButton, false);

        JLabel templatesLabel = new JLabel("Templates");
        templatesLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));

        JPanel templateActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        templateActions.setOpaque(false);
        templateActions.add(newButton);
        templateActions.add(duplicateButton);
        templateActions.add(moreActionsButton);

        JPanel templateHeader = new JPanel(new BorderLayout(8, 0));
        templateHeader.setOpaque(false);
        templateHeader.add(templatesLabel, BorderLayout.WEST);
        templateHeader.add(templateActions, BorderLayout.EAST);

        JPanel templatePanel = new JPanel(new BorderLayout(0, 10));
        templatePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 0));
        templatePanel.add(templateHeader, BorderLayout.NORTH);
        templatePanel.add(new JScrollPane(templateList), BorderLayout.CENTER);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightButtons.add(saveButton);
        rightButtons.add(closeButton);

        JPanel chooserPanelWrapper = new JPanel(new BorderLayout(0, 10));
        chooserPanelWrapper.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));
        chooserPanelWrapper.add(chooserPanel, BorderLayout.CENTER);
        chooserPanelWrapper.add(rightButtons, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, templatePanel, chooserPanelWrapper);
        splitPane.setBorder(null);
        splitPane.setResizeWeight(0.22);
        splitPane.setDividerLocation(280);

        setContentPane(splitPane);
    }

    private void registerKeyboardActions() {
        getRootPane().registerKeyboardAction(__ -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().setDefaultButton(saveButton);
    }

    private void refreshTemplates(UUID preferredTemplateKey) {
        templateListModel.clear();
        try {
            templateCatalog.listTemplates().forEach(templateListModel::addElement);
        } catch (RuntimeException ex) {
            clearSelection();
            resetChooser();
            showTemplateError("Unable to load chart templates.", ex);
            return;
        }

        if (templateListModel.isEmpty()) {
            clearSelection();
            resetChooser();
            return;
        }

        int selectionIndex = 0;
        UUID selectedKey = preferredTemplateKey;
        if (selectedKey == null && selectedTemplate != null)
            selectedKey = selectedTemplate.templateKey();

        for (int i = 0; i < templateListModel.size(); i++) {
            ChartTemplateSummary template = templateListModel.get(i);
            if (selectedKey != null && selectedKey.equals(template.templateKey())) {
                selectionIndex = i;
                break;
            }
            if (selectedKey == null && template.defaultTemplate()) {
                selectionIndex = i;
                break;
            }
        }
        templateList.setSelectedIndex(selectionIndex);
    }

    private void loadSelectedTemplate() {
        selectedTemplate = templateList.getSelectedValue();
        if (selectedTemplate == null) {
            clearSelection();
            resetChooser();
            return;
        }

        try {
            showLoadedTemplate(templateCatalog.getTemplate(selectedTemplate.templateKey()));
        } catch (RuntimeException ex) {
            clearLoadedTemplateState();
            resetChooser();
            showTemplateError("Unable to load the selected template.", ex);
        }
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedTemplate != null;
        boolean editable = hasEditableSelection();

        duplicateButton.setEnabled(hasLoadedSelection());
        moreActionsButton.setEnabled(hasOverflowActions(hasSelection, editable));
        saveButton.setEnabled(editable);
    }

    private boolean hasOverflowActions(boolean hasSelection, boolean editable) {
        if (!hasSelection)
            return false;
        return editable
                || !selectedTemplate.defaultTemplate()
                || selectedTemplate.builtIn();
    }

    private void onNewTemplate(ActionEvent event) {
        createTemplate("Create Template", suggestTemplateName("New Template"), "Unable to create the chart template.");
    }

    private void onDuplicateTemplate(ActionEvent event) {
        if (!hasLoadedSelection())
            return;

        createTemplate(
                "Duplicate Template",
                suggestTemplateName(selectedTemplate.name() + " Copy"),
                "Unable to duplicate the chart template."
        );
    }

    private void onMoreActions(ActionEvent event) {
        JPopupMenu menu = buildMoreActionsMenu();
        if (menu.getComponentCount() == 0)
            return;
        menu.show(moreActionsButton, 0, moreActionsButton.getHeight());
    }

    private JPopupMenu buildMoreActionsMenu() {
        JPopupMenu menu = new JPopupMenu();
        if (selectedTemplate == null)
            return menu;

        if (hasEditableSelection())
            menu.add(menuItem("Rename", this::onRenameTemplate));
        if (selectedTemplate.editable())
            menu.add(menuItem("Delete", this::onDeleteTemplate));
        if (!selectedTemplate.defaultTemplate()) {
            if (menu.getComponentCount() > 0)
                menu.addSeparator();
            menu.add(menuItem("Set as Default", this::onSetDefaultTemplate));
        }
        if (selectedTemplate.builtIn()) {
            if (menu.getComponentCount() > 0)
                menu.addSeparator();
            menu.add(menuItem("Restore Built-in", this::onRestoreBuiltIn));
        }
        return menu;
    }

    private void onRenameTemplate(ActionEvent event) {
        if (!hasEditableSelection())
            return;

        while (true) {
            String name = promptForName("Rename Template", "Template name:", selectedTemplate.name());
            if (name == null)
                return;

            try {
                ChartTemplateSummary updated = updateSelectedTemplate(name);
                refreshTemplates(updated.templateKey());
                return;
            } catch (RuntimeException ex) {
                showTemplateError("Unable to rename the chart template.", ex);
            }
        }
    }

    private void onDeleteTemplate(ActionEvent event) {
        if (selectedTemplate == null || !selectedTemplate.editable())
            return;

        NotifyDescriptor descriptor = new NotifyDescriptor.Confirmation(
                "Delete template '" + selectedTemplate.name() + "'?",
                "Delete Template",
                NotifyDescriptor.YES_NO_OPTION);
        if (!NotifyDescriptor.YES_OPTION.equals(DialogDisplayer.getDefault().notify(descriptor)))
            return;

        try {
            templateCatalog.deleteTemplate(selectedTemplate.templateKey());
            refreshTemplates(null);
        } catch (RuntimeException ex) {
            showTemplateError("Unable to delete the chart template.", ex);
        }
    }

    private void onSetDefaultTemplate(ActionEvent event) {
        if (selectedTemplate == null)
            return;

        try {
            ChartTemplateSummary updated = templateCatalog.setDefaultTemplate(selectedTemplate.templateKey());
            refreshTemplates(updated.templateKey());
        } catch (RuntimeException ex) {
            showTemplateError("Unable to set the chart template as default.", ex);
        }
    }

    private void onRestoreBuiltIn(ActionEvent event) {
        try {
            ChartTemplateSummary restored = templateCatalog.restoreBuiltIn();
            refreshTemplates(restored.templateKey());
        } catch (RuntimeException ex) {
            showTemplateError("Unable to restore the built-in template.", ex);
        }
    }

    private void onSaveTemplate(ActionEvent event) {
        if (!hasEditableSelection())
            return;

        try {
            ChartTemplateSummary updated = updateSelectedTemplate(selectedTemplate.name());
            refreshTemplates(updated.templateKey());
        } catch (RuntimeException ex) {
            showTemplateError("Unable to save the chart template.", ex);
        }
    }

    private void createTemplate(String title, String suggestedName, String errorTitle) {
        while (true) {
            String name = promptForName(title, "Template name:", suggestedName);
            if (name == null)
                return;

            try {
                ChartTemplateSummary created = templateCatalog.createTemplate(buildTemplateSnapshot(name));
                refreshTemplates(created.templateKey());
                return;
            } catch (RuntimeException ex) {
                showTemplateError(errorTitle, ex);
            }
        }
    }

    private boolean hasLoadedSelection() {
        return selectedTemplate != null && selectedTemplateLoaded;
    }

    private boolean hasEditableSelection() {
        return hasLoadedSelection() && selectedTemplate.editable();
    }

    private ChartTemplateSummary updateSelectedTemplate(String name) {
        return templateCatalog.updateTemplate(selectedTemplate.templateKey(), buildTemplateSnapshot(name));
    }

    private ChartTemplate buildTemplateSnapshot(String name) {
        ChartPluginSelection selection = chooserPanel.getSelection();
        if (loadedChartTemplate != null)
            return ChartTemplateSelectionSnapshotFactory.create(name, loadedChartTemplate, selection);
        return ChartTemplateSelectionSnapshotFactory.create(name, selection);
    }

    private void clearSelection() {
        selectedTemplate = null;
        clearLoadedTemplateState();
    }

    private void clearLoadedTemplateState() {
        selectedTemplateLoaded = false;
        loadedChartTemplate = null;
    }

    private void resetChooser() {
        chooserPanel.initForm(allIndicators, List.of(), allOverlays, List.of());
        updateButtonStates();
    }

    private void showLoadedTemplate(ChartTemplateCatalog.LoadedTemplate loadedTemplate) {
        ChartTemplate chartTemplate = loadedTemplate.chartTemplate();
        chooserPanel.initForm(
                allIndicators,
                chartTemplate.getIndicators(),
                allOverlays,
                chartTemplate.getOverlays()
        );
        loadedChartTemplate = chartTemplate;
        selectedTemplateLoaded = true;
    }

    private String promptForName(String title, String message, String initialValue) {
        while (true) {
            String value = (String) JOptionPane.showInputDialog(
                    this,
                    message,
                    title,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    initialValue);
            if (value == null)
                return null;

            String normalized = value.strip();
            if (!normalized.isEmpty())
                return normalized;
        }
    }

    private String suggestTemplateName(String baseName) {
        String base = Objects.requireNonNull(baseName, "baseName").strip();
        String candidate = base.isEmpty() ? "Template" : base;

        int suffix = 2;
        LinkedHashSet<String> existingNames = new LinkedHashSet<>();
        for (int i = 0; i < templateListModel.size(); i++)
            existingNames.add(templateListModel.get(i).name().toLowerCase(Locale.ROOT));
        String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
        while (existingNames.contains(normalizedCandidate)) {
            candidate = base + " " + suffix++;
            normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
        }
        return candidate;
    }

    private void showTemplateError(String title, RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank())
            message = title;
        else
            message = title + "\n" + message;
        JOptionPane.showMessageDialog(this, message, "Template Error", JOptionPane.ERROR_MESSAGE);
    }

    private static Window resolveOwner(Component locationAnchor) {
        Window owner = (locationAnchor != null) ? SwingUtilities.getWindowAncestor(locationAnchor) : null;
        if (owner != null)
            return owner;
        return Optional.ofNullable(WindowManager.getDefault()).map(WindowManager::getMainWindow).orElse(null);
    }

    private static JMenuItem menuItem(String text, java.awt.event.ActionListener listener) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(listener);
        return item;
    }

    private static void configureHeaderButton(JButton button, boolean emphasized) {
        button.setFocusable(false);
        button.setMargin(new Insets(6, emphasized ? 14 : 10, 6, emphasized ? 14 : 10));
        button.putClientProperty("JButton.buttonType", emphasized ? "roundRect" : "toolBarButton");
    }
}
