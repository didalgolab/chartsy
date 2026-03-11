/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.Objects;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.MatteBorder;

import one.chartsy.SymbolIdentity;
import one.chartsy.data.CandleSeries;
import one.chartsy.ui.chart.AppliedChartTemplateRef;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.ChartFrameListener;
import one.chartsy.ui.chart.ChartTemplateCatalog;
import one.chartsy.ui.chart.ChartTemplateSummary;
import one.chartsy.ui.chart.action.ChartActions;

/**
 * Provides the toolbar functionality to the chart frame.
 *
 * @author Mariusz Bernacki
 */
public class ChartToolbar extends JToolBar implements Serializable {

    private final ChartFrame chartFrame;
    private SymbolChanger symbolChanger;
    private TimeFrameSelector timeFrameSelector;
    private Action favoriteAction;
    private JButton templatesButton;
    
    public ChartToolbar(ChartFrame frame) {
        super("ChartToolbar", JToolBar.HORIZONTAL);
        this.chartFrame = frame;
        initComponents();
        setFloatable(false);
        setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x898c95)));
        setComponentPopupMenu(new JPopupMenu());
        //setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
    }
    
    private JButton annotationButton;
    
    private void initComponents() {
        // SymbolChanger toolbar
        symbolChanger = new SymbolChanger(chartFrame);
        add(symbolChanger);

        timeFrameSelector = new TimeFrameSelector(chartFrame);
        add(timeFrameSelector);

        // ChartToolbar buttons
        addSeparator();
        add(createButton(ChartActions.zoomIn(chartFrame)));
        add(createButton(ChartActions.zoomOut(chartFrame)));
        add(createButton(ChartActions.openIndicators(chartFrame)));
        add(createTemplatesButton());
//        add(createButton(ChartActions.intervalPopup(chartFrame)));
//        add(createButton(ChartActions.chartPopup(chartFrame)));
//        add(createButton(ChartActions.openOverlays(chartFrame)));
        add(annotationButton = createButton(ChartActions.annotationPopup(chartFrame)));
//        JToggleButton markerButton = createToggleButton(ChartActions.toggleMarker(chartFrame));
        //add(markerButton);
//        add(createButton(ChartActions.exportImage(chartFrame)));
//        add(createButton(ChartActions.printChart(chartFrame)));
//        add(createButton(ChartActions.chartProperties(chartFrame)));
//        addSeparator();
//        add(createButton(favoriteAction = ChartActions.addToFavorites(chartFrame)));
//
//        markerButton.setSelected(false);
        chartFrame.addChartFrameListener(new ChartFrameListener() {
            @Override
            public void datasetChanged(CandleSeries quotes) {
                SwingUtilities.invokeLater(() -> updateFavoriteActionState(quotes.getResource().symbol()));
            }
        });
        chartFrame.addPropertyChangeListener(ChartFrame.APPLIED_TEMPLATE_PROPERTY, __ -> updateTemplatesButtonState());
        chartFrame.addPropertyChangeListener(ChartFrame.TEMPLATE_DIRTY_PROPERTY, __ -> updateTemplatesButtonState());
        
        // if the quotes are already loaded update the favorite action as soon as possible
        CandleSeries dataset = chartFrame.getChartData().getDataset();
        if (dataset != null)
            SwingUtilities.invokeLater(() -> updateFavoriteActionState(dataset.getResource().symbol()));
        updateTemplatesButtonState();
    }
    
    public void doBack() {
        symbolChanger.getBackButton().doClick();
    }
    
    public void doForward() {
        symbolChanger.getForwardButton().doClick();
    }
    
    protected void updateFavoriteActionState(SymbolIdentity symbol) {
//        // symbols without data provider are not supported
//        DataProvider provider = symbol.getProvider();
//        if (provider == null) {
//            favoriteAction.setEnabled(false);
//            return;
//        }
//
//        SymbolLinkSearchCriteria criteria = new SymbolLinkSearchCriteria();
//        criteria.setName(symbol.getName());
//        criteria.setDpiUuid(provider.getUuid());
//        criteria.setSgrStereotype(Stereotype.FAVORITES);
//
//        SymbolLinkRepository repo = Lookup.getDefault().lookup(SymbolLinkRepository.class);
//        List<SymbolLinkData> symbolLinks = repo.findSymbolLinks(criteria);
//
//        // there shouldn't be more than one favorite link
//        SymbolLinkData symbolLink = symbolLinks.isEmpty()? null : symbolLinks.get(0);
//        favoriteAction.putValue(FavoriteAction.SYMBOL_LINK, symbolLink);
//        favoriteAction.setEnabled(true);
    }
    
    public void updateToolbar() {
        symbolChanger.updateToolbar();
    }
    
    public void applyVisualStyle(Container cont) {
        visualStyleController.applyVisualStyle(cont);
    }
    
    /**
     * Sets the {@code hideActionText} property, which determines
     * whether all buttons contained in this toolbar will display text from their <code>Action</code>'s.
     *
     * @param hideActionText <code>true</code> if the buttons
     *                       <code>text</code> property should not reflect
     *                       that of the <code>Action</code>; the default is
     *                       <code>false</code>
     */
    public void setHideActionText(boolean hideActionText) {
        for (int i = 0; i < getComponentCount(); i++) {
            Component comp = getComponent(i);
            if (comp instanceof AbstractButton)
                ((AbstractButton) comp).setHideActionText(hideActionText);
        }
        revalidate();
        repaint();
    }
    
    final class VisualStyleController implements PropertyChangeListener {
        /** Indicates that the toolbar displays large icons. */
        private boolean showLargeIcons = false;
        
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (AbstractButton.ICON_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                AbstractButton button = (AbstractButton) evt.getSource();
                
                // retrieve icon from action
                Icon icon = getIconFromAction(button.getAction());
                
                // check if icon is different than expected
                if (icon != null && icon != evt.getNewValue())
                    button.setIcon(icon);
            }
        }
        
        private Icon getIconFromAction(Action action) {
            Icon icon = null;
            if (showLargeIcons)
                icon = (Icon) action.getValue(Action.LARGE_ICON_KEY);
            if (icon == null)
                icon = (Icon) action.getValue(Action.SMALL_ICON);
            return icon;
        }
        
        public void setShowLargeIcons(boolean showLargeIcons) {
            this.showLargeIcons = showLargeIcons;
            applyVisualStyle(showLargeIcons, ChartToolbar.this);
        }
        
        void applyVisualStyle(Container cont) {
            applyVisualStyle(showLargeIcons, cont);
        }
        
        private void applyVisualStyle(boolean showLargeIcons, Container cont) {
            Insets margin = new Insets(0, 0, 0, 0);
            if (showLargeIcons)
                margin.bottom = margin.left = margin.right = margin.top = 6;
            
            for (int i = 0; i < cont.getComponentCount(); i++) {
                Component comp = cont.getComponent(i);
                if (comp instanceof AbstractButton) {
                    AbstractButton b = (AbstractButton) comp;
                    b.setIcon(getIconFromAction(b.getAction()));
                    b.setVerticalTextPosition(showLargeIcons? SwingConstants.BOTTOM : SwingConstants.CENTER);
                    b.setHorizontalTextPosition(showLargeIcons? SwingConstants.CENTER : SwingConstants.RIGHT);
                    if (cont != symbolChanger) {
                        if (!b.getHideActionText())
                            margin.left = margin.right = 3;
                        b.setMargin(margin);
                    }
                    b.setIconTextGap(showLargeIcons? 4 : 2);
                } else if (comp instanceof SymbolChanger) {
                    applyVisualStyle(showLargeIcons, (SymbolChanger) comp);
                }
            }
        }
    }
    private final VisualStyleController visualStyleController = new VisualStyleController();
    
    public void toggleIcons() {
        boolean small = chartFrame.getChartProperties().getToolbarSmallIcons();
        visualStyleController.setShowLargeIcons(!small);
        revalidate();
        repaint();
    }
    
    @Override
    public JPopupMenu getComponentPopupMenu() {
        JPopupMenu popup = new JPopupMenu();
        JCheckBoxMenuItem item;
        
        popup.add(item = new JCheckBoxMenuItem(ChartActions.toggleToolbarSmallIcons(chartFrame, this)));
        item.setState(chartFrame.getChartProperties().getToolbarSmallIcons());
        
        popup.add(item = new JCheckBoxMenuItem(ChartActions.toggleToolbarShowLabels(chartFrame, this)));
        item.setState(!chartFrame.getChartProperties().getToolbarShowLabels());
        popup.add(ChartActions.toggleToolbarVisibility(chartFrame));

        return popup;
    }
    
    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.width /= 2;
        return size;
    }
    
    @Override
    public Dimension getMinimumSize() {
        Dimension size = super.getMinimumSize();
        size.width /= 2;
        return size;
    }
    
    protected JButton createButton(Action action) {
        return configureButton(new JButton(action));
    }

    protected JButton createTemplatesButton() {
        templatesButton = new JButton("Templates");
        templatesButton.setFocusable(false);
        templatesButton.setMargin(new Insets(4, 8, 4, 8));
        templatesButton.addActionListener(event -> {
            JPopupMenu popup = buildTemplatesMenu();
            popup.show(templatesButton, 0, templatesButton.getHeight());
        });
        return templatesButton;
    }
    
    protected JToggleButton createToggleButton(Action action) {
        return configureButton(new JToggleButton(action));
    }
    
    protected <T extends AbstractButton> T configureButton(T b) {
        b.addPropertyChangeListener(AbstractButton.ICON_CHANGED_PROPERTY, visualStyleController);
        b.setVerticalAlignment(SwingConstants.TOP);
        b.setVerticalTextPosition(SwingConstants.BOTTOM);
        b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setMargin(new Insets(4, 4, 4, 4));
        b.setBorderPainted(false);
        b.setFocusable(false);
        b.setText(null);
        
        return b;
    }

    private JPopupMenu buildTemplatesMenu() {
        JPopupMenu popup = new JPopupMenu();
        AppliedChartTemplateRef appliedTemplate = chartFrame.getAppliedChartTemplate();
        boolean dirty = chartFrame.isTemplateDirty();

        JMenu applyTemplateMenu = buildApplyTemplateMenu(appliedTemplate);
        if (applyTemplateMenu != null)
            popup.add(applyTemplateMenu);
        popup.add(menuItem("Manage Templates...", __ -> openTemplateManager()));
        popup.add(menuItem("Save Current as New Template...", __ -> saveCurrentAsNewTemplate()));

        if (appliedTemplate != null && !appliedTemplate.builtIn() && dirty)
            popup.add(menuItem("Update Template", __ -> updateCurrentTemplate()));

        if (appliedTemplate != null && !appliedTemplate.defaultTemplate())
            popup.add(menuItem("Set as Default", __ -> setCurrentTemplateAsDefault()));

        if (appliedTemplate != null && dirty) {
            popup.add(menuItem("Revert to Template", __ -> {
                chartFrame.revertToAppliedTemplate();
                updateTemplatesButtonState();
            }));
        }
        return popup;
    }

    private JMenu buildApplyTemplateMenu(AppliedChartTemplateRef appliedTemplate) {
        var templates = ChartTemplateCatalog.getDefault().listTemplates();
        if (templates.size() <= 1)
            return null;

        JMenu menu = new JMenu("Apply Template");
        for (ChartTemplateSummary template : templates) {
            String label = template.name();
            if (template.defaultTemplate())
                label += " (Default)";

            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label);
            boolean currentTemplate = appliedTemplate != null
                    && Objects.equals(appliedTemplate.templateKey(), template.templateKey());
            item.setSelected(currentTemplate);
            item.setEnabled(!currentTemplate);
            item.addActionListener(__ -> applyTemplate(template.templateKey()));
            menu.add(item);
        }
        return menu;
    }

    private JMenuItem menuItem(String label, java.awt.event.ActionListener listener) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(listener);
        return item;
    }

    private void openTemplateManager() {
        AppliedChartTemplateRef appliedTemplate = chartFrame.getAppliedChartTemplate();
        ChartTemplateManagerDialog dialog = new ChartTemplateManagerDialog(
                chartFrame,
                (appliedTemplate != null) ? appliedTemplate.templateKey() : null);
        dialog.setLocationRelativeTo(chartFrame);
        dialog.setVisible(true);
        syncAppliedTemplateMetadata();
    }

    private void applyTemplate(java.util.UUID templateKey) {
        var loadedTemplate = ChartTemplateCatalog.getDefault().getTemplate(templateKey);
        chartFrame.applyLoadedTemplate(loadedTemplate);
        updateTemplatesButtonState();
    }

    private void saveCurrentAsNewTemplate() {
        String baseName = (chartFrame.getAppliedChartTemplate() != null)
                ? chartFrame.getAppliedChartTemplate().name() + " Copy"
                : "Template";
        String name = promptForName("Save Current as New Template", baseName);
        if (name == null)
            return;

        ChartTemplateSummary created = ChartTemplateCatalog.getDefault()
                .createTemplate(name, captureCurrentSelection());
        var loadedTemplate = ChartTemplateCatalog.getDefault().getTemplate(created.templateKey());
        chartFrame.setAppliedChartTemplate(loadedTemplate.summary(), loadedTemplate.payload());
        chartFrame.refreshTemplateState();
        updateTemplatesButtonState();
    }

    private void updateCurrentTemplate() {
        AppliedChartTemplateRef appliedTemplate = chartFrame.getAppliedChartTemplate();
        if (appliedTemplate == null || appliedTemplate.builtIn())
            return;

        ChartTemplateCatalog catalog = ChartTemplateCatalog.getDefault();
        ChartTemplateSummary updated = catalog.updateTemplate(
                appliedTemplate.templateKey(),
                appliedTemplate.name(),
                captureCurrentSelection());
        var loadedTemplate = catalog.getTemplate(updated.templateKey());
        chartFrame.setAppliedChartTemplate(loadedTemplate.summary(), loadedTemplate.payload());
        chartFrame.refreshTemplateState();
        updateTemplatesButtonState();
    }

    private void setCurrentTemplateAsDefault() {
        AppliedChartTemplateRef appliedTemplate = chartFrame.getAppliedChartTemplate();
        if (appliedTemplate == null)
            return;

        ChartTemplateCatalog catalog = ChartTemplateCatalog.getDefault();
        ChartTemplateSummary updated = catalog.setDefaultTemplate(appliedTemplate.templateKey());
        var loadedTemplate = catalog.getTemplate(updated.templateKey());
        chartFrame.setAppliedChartTemplate(loadedTemplate.summary(), loadedTemplate.payload());
        chartFrame.refreshTemplateState();
        updateTemplatesButtonState();
    }

    private void syncAppliedTemplateMetadata() {
        AppliedChartTemplateRef appliedTemplate = chartFrame.getAppliedChartTemplate();
        if (appliedTemplate == null) {
            updateTemplatesButtonState();
            return;
        }

        try {
            var loadedTemplate = ChartTemplateCatalog.getDefault().getTemplate(appliedTemplate.templateKey());
            chartFrame.setAppliedChartTemplate(loadedTemplate.summary(), loadedTemplate.payload());
            chartFrame.refreshTemplateState();
        } catch (IllegalArgumentException ex) {
            chartFrame.log().warn("Template `{}` is no longer available", appliedTemplate.templateKey(), ex);
        }
        updateTemplatesButtonState();
    }

    private ChartPluginSelection captureCurrentSelection() {
        var stackPanel = chartFrame.getMainStackPanel();
        return new ChartPluginSelection(
                stackPanel.getIndicatorsList(),
                stackPanel.getChartPanel().getOverlays());
    }

    private String promptForName(String title, String initialValue) {
        while (true) {
            String value = (String) JOptionPane.showInputDialog(
                    chartFrame,
                    "Template name:",
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

    private void updateTemplatesButtonState() {
        if (templatesButton == null)
            return;
        templatesButton.setText(compactLabel(currentTemplateLabel(chartFrame.getAppliedChartTemplate(), chartFrame.isTemplateDirty())));
    }

    private String currentTemplateLabel(AppliedChartTemplateRef appliedTemplate, boolean dirty) {
        if (appliedTemplate == null)
            return "Templates";
        return appliedTemplate.name() + (dirty ? "*" : "");
    }

    private String compactLabel(String label) {
        if (label.length() <= 24)
            return label;
        return label.substring(0, 21) + "...";
    }
    
    /**
     * @return the annotationButton
     */
    public JButton getAnnotationButton() {
        return annotationButton;
    }
}
