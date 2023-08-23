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

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.MatteBorder;

import one.chartsy.SymbolIdentity;
import one.chartsy.data.CandleSeries;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.ChartFrameListener;
import one.chartsy.ui.chart.action.ChartActions;

/**
 * Provides the toolbar functionality to the chart frame.
 *
 * @author Mariusz Bernacki
 */
public class ChartToolbar extends JToolBar implements Serializable {

    private final ChartFrame chartFrame;
    private SymbolChanger symbolChanger;
    private Action favoriteAction;
    
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
        
        // ChartToolbar buttons
        addSeparator();
        add(createButton(ChartActions.zoomIn(chartFrame)));
        add(createButton(ChartActions.zoomOut(chartFrame)));
//        add(createButton(ChartActions.intervalPopup(chartFrame)));
//        add(createButton(ChartActions.chartPopup(chartFrame)));
//        add(createButton(ChartActions.openOverlays(chartFrame)));
//        add(createButton(ChartActions.openIndicators(chartFrame)));
//        add(annotationButton = createButton(ChartActions.annotationPopup(chartFrame)));
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
        
        // if the quotes are already loaded update the favorite action as soon as possible
        CandleSeries dataset = chartFrame.getChartData().getDataset();
        if (dataset != null)
            SwingUtilities.invokeLater(() -> updateFavoriteActionState(dataset.getResource().symbol()));
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
        private boolean showLargeIcons = true;
        
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
    
    protected JToggleButton createToggleButton(Action action) {
        return configureButton(new JToggleButton(action));
    }
    
    protected <T extends AbstractButton> T configureButton(T b) {
        b.addPropertyChangeListener(AbstractButton.ICON_CHANGED_PROPERTY, visualStyleController);
        b.setVerticalAlignment(SwingConstants.TOP);
        b.setVerticalTextPosition(SwingConstants.BOTTOM);
        b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setMargin(new Insets(6, 6, 6, 6));
        b.setBorderPainted(false);
        b.setFocusable(false);
        
        return b;
    }
    
    /**
     * @return the annotationButton
     */
    public JButton getAnnotationButton() {
        return annotationButton;
    }
}
