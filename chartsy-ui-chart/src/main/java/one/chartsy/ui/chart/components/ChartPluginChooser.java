/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.LayoutStyle;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import one.chartsy.core.NamedPlugin;
import one.chartsy.core.ObjectInstantiator;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.properties.NamedPluginNode;
import org.openide.explorer.propertysheet.PropertySheet;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;

/**
 * The generic chart plugin chooser dialog.
 * 
 * @author Mariusz Bernacki
 *
 * @param <T>
 *            the type of the chart plugin handled, e.g. {@code Indicator},
 *            {@code Overlay},... etc.
 */
public class ChartPluginChooser<T extends NamedPlugin<T> & ObjectInstantiator<T>> extends JDialog {
    @Serial
    private static final long serialVersionUID = 5971773443821473740L;
    /** The parental chart frame that owns this chooser. */
    private final ChartFrame parent;
    /** The list of currently selected plugins. */
    private final DefaultListModel<T> selectedPlugins = new DefaultListModel<>();
    /** The list of all plugins available for selection. */
    private final DefaultListModel<T> availablePlugins = new DefaultListModel<>();
    /** The callback object which handles the chosen and accepted selection. */
    private final Consumer<List<T>> selectionHandler;
    /** The label for the available plugins list. */
    private final JLabel availablePluginsLabel = new JLabel();
    /** The label for the selected plugins list. */
    private final JLabel selectedPluginsLabel = new JLabel();
    /** The available plugins list. */
    private final JList<T> availablePluginsList = new JList<>(availablePlugins);
    /** The selected plugins list. */
    private final JList<T> selectedPluginsList = new JList<>(selectedPlugins);
    /** The option button for adding a plugin to the selected plugins list. */
    private final JButton addOption = new JButton();
    /** The option button for applying changes to the chart frame. */
    private final JButton applyOption = new JButton();
    /** The option button for canceling changes and dismissing the chooser. */
    private final JButton cancelOption = new JButton();
    /** The option button for accepting all changes and closing the chooser. */
    private final JButton finishOption = new JButton();
    /** The option button for removing the plugin from the selected plugins list. */
    private final JButton removeOption = new JButton();
    
    
    /**
     * Creates new form ChartPluginChooser dialog.
     * 
     * @param parent
     *            the parental chart frame that owns this chooser
     * @param selectionHandler
     *            the callback object which handles the chosen and accepted
     *            selection
     */
    public ChartPluginChooser(ChartFrame parent, Consumer<List<T>> selectionHandler) {
        super(WindowManager.getDefault().getMainWindow());
        this.parent = parent;
        this.selectionHandler = selectionHandler;
        setTitle(NbBundle.getMessage(ChartPluginChooser.class, "ChPChooser.title")); // NOI18N
        setModalityType(ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        initComponents();
        registerKeyboardActions();
        registerWindowActions();
    }
    
    protected void registerKeyboardActions() {
        getRootPane().registerKeyboardAction(__ -> fireWindowClosing(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
    
    protected void registerWindowActions() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                //parent.componentFocused();
            }
        });
    }
    
    protected void fireWindowClosing() {
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }
    
    public void initForm(Collection<T> allPlugins, Collection<T> selectedPlugins) {
        addOption.setEnabled(false);
        removeOption.setEnabled(false);
        
        for (T plugin : allPlugins)
            this.availablePlugins.addElement(plugin);
        for (T plugin : selectedPlugins)
            this.selectedPlugins.addElement(plugin);
        
        scrollPane.setEnabled(false);
        scrollPane.setLayout(new BorderLayout());
        scrollPane.setPreferredSize(new Dimension(550, 300));
        scrollPane.setMinimumSize(new Dimension(550, 300));
    }
    
    private void unsetPropertySheet() {
        if (scrollPane.getComponentCount() > 0 && scrollPane.getComponent(0) instanceof PropertySheet) {
            PropertySheet propertySheet = (PropertySheet) scrollPane.getComponent(0);
            propertySheet.setNodes(null);
            repaint();
        }
    }
    
    private void setPropertySheet(T i) {
        PropertySheet prop = new PropertySheet();
        prop.setNodes(new Node[] { new NamedPluginNode<>(i) });
        
        scrollPane.setEnabled(true);
        scrollPane.removeAll();
        scrollPane.add(prop, BorderLayout.CENTER);
        scrollPane.revalidate();
        repaint();
    }
    
    private void initComponents() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        jScrollPane1 = new JScrollPane();
        jScrollPane2 = new JScrollPane();
        propertiesLabel = new JLabel();
        scrollPane = new JPanel();
        jSeparator1 = new JSeparator();
        
        selectedPluginsLabel.setText(NbBundle.getMessage(ChartPluginChooser.class, "ChPChooser.selectedLabel.text")); // NOI18N
        
        availablePluginsLabel.setFont(new Font("Tahoma", 1, 11)); // NOI18N
        availablePluginsLabel.setText(getTitle().concat(":")); // NOI18N
        
        availablePluginsList.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 8962098510030100441L;
            
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object item, int index, boolean isSelected,
                    boolean cellHasFocus) {
                String value = ((NamedPlugin<?>) item).getName();
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });
        availablePluginsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                lstUnselectedMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(availablePluginsList);
        
        selectedPluginsLabel.setFont(new Font("Tahoma", 1, 11));
        
        selectedPluginsList.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = -6628078062921861425L;
            
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object item, int index, boolean isSelected,
                    boolean cellHasFocus) {
                String value = ((NamedPlugin<?>) item).getLabel();
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });
        selectedPluginsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                lstSelectedMouseClicked(evt);
            }
        });
        jScrollPane2.setViewportView(selectedPluginsList);
        
        addOption.setText(NbBundle.getMessage(ChartPluginChooser.class, "ChPChooser.btnAdd.text")); // NOI18N
        addOption.addActionListener(this::onAdd);
        
        removeOption.setText(NbBundle.getMessage(ChartPluginChooser.class, "ChPChooser.btnRemove.text")); // NOI18N
        removeOption.addActionListener(this::onRemove);
        
        propertiesLabel.setFont(new Font("Tahoma", 1, 11));
        propertiesLabel.setText(NbBundle.getMessage(
                ChartPluginChooser.class, "ChPChooser.lblProperties.text")); // NOI18N
        
        scrollPane.setBackground(new Color(255, 255, 255));
        scrollPane.setBorder(javax.swing.BorderFactory
                .createLineBorder(new Color(153, 153, 153)));
        scrollPane.setAutoscrolls(true);
        scrollPane
        .setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        
        GroupLayout scrollPaneLayout = new GroupLayout(
                scrollPane);
        scrollPane.setLayout(scrollPaneLayout);
        scrollPaneLayout.setHorizontalGroup(scrollPaneLayout
                .createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGap(0, 548, Short.MAX_VALUE));
        scrollPaneLayout.setVerticalGroup(scrollPaneLayout.createParallelGroup(
                GroupLayout.Alignment.LEADING).addGap(0, 296,
                        Short.MAX_VALUE));
        
        jSeparator1.setOrientation(SwingConstants.VERTICAL);
        
        finishOption.setText(NbBundle.getMessage(ChartPluginChooser.class,
                "ChPChooser.btnOk.text")); // NOI18N
        finishOption.addActionListener(this::onFinish);
        
        applyOption.setText(NbBundle.getMessage(ChartPluginChooser.class,
                "ChPChooser.btnApply.text")); // NOI18N
        applyOption.addActionListener(this::applyChanges);
        
        cancelOption.setText(NbBundle.getMessage(
                ChartPluginChooser.class, "ChPChooser.btnCancel.text")); // NOI18N
        cancelOption.addActionListener(this::onCancel);
        
        GroupLayout layout = new GroupLayout(
                getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout
                .createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGap(0, 897, Short.MAX_VALUE)
                .addGroup(
                        layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                                layout.createParallelGroup(
                                        GroupLayout.Alignment.LEADING)
                                .addGroup(
                                        layout.createSequentialGroup()
                                        .addComponent(
                                                addOption)
                                        .addPreferredGap(
                                                LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(
                                                removeOption))
                                .addComponent(
                                        jScrollPane2,
                                        GroupLayout.PREFERRED_SIZE,
                                        298,
                                        GroupLayout.PREFERRED_SIZE)
                                .addGroup(
                                        layout.createParallelGroup(
                                                GroupLayout.Alignment.TRAILING,
                                                false)
                                        .addComponent(
                                                selectedPluginsLabel,
                                                GroupLayout.Alignment.LEADING,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                Short.MAX_VALUE)
                                        .addComponent(
                                                availablePluginsLabel,
                                                GroupLayout.Alignment.LEADING,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                Short.MAX_VALUE)
                                        .addComponent(
                                                jScrollPane1,
                                                GroupLayout.Alignment.LEADING,
                                                GroupLayout.DEFAULT_SIZE,
                                                298,
                                                Short.MAX_VALUE)))
                        .addGap(16, 16, 16)
                        .addComponent(jSeparator1,
                                GroupLayout.PREFERRED_SIZE,
                                GroupLayout.DEFAULT_SIZE,
                                GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(
                                LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(
                                layout.createParallelGroup(
                                        GroupLayout.Alignment.LEADING)
                                .addGroup(
                                        layout.createParallelGroup(
                                                GroupLayout.Alignment.TRAILING)
                                        .addGroup(
                                                layout.createSequentialGroup()
                                                .addComponent(
                                                        finishOption)
                                                .addPreferredGap(
                                                        LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(
                                                        applyOption)
                                                .addPreferredGap(
                                                        LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(
                                                        cancelOption))
                                        .addComponent(
                                                scrollPane,
                                                GroupLayout.PREFERRED_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.PREFERRED_SIZE))
                                .addComponent(
                                        propertiesLabel,
                                        GroupLayout.PREFERRED_SIZE,
                                        151,
                                        GroupLayout.PREFERRED_SIZE))
                        .addGap(16, 16, 16)));
        layout.setVerticalGroup(layout
                .createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGap(0, 372, Short.MAX_VALUE)
                .addGroup(
                        layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                                layout.createParallelGroup(
                                        GroupLayout.Alignment.LEADING)
                                .addComponent(
                                        jSeparator1,
                                        GroupLayout.DEFAULT_SIZE,
                                        350, Short.MAX_VALUE)
                                .addGroup(
                                        layout.createSequentialGroup()
                                        .addComponent(
                                                propertiesLabel)
                                        .addGap(11, 11,
                                                11)
                                        .addComponent(
                                                scrollPane,
                                                GroupLayout.PREFERRED_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(
                                                LayoutStyle.ComponentPlacement.RELATED,
                                                GroupLayout.DEFAULT_SIZE,
                                                Short.MAX_VALUE)
                                        .addGroup(
                                                layout.createParallelGroup(
                                                        GroupLayout.Alignment.BASELINE)
                                                .addComponent(
                                                        cancelOption)
                                                .addComponent(
                                                        applyOption)
                                                .addComponent(
                                                        finishOption)))
                                .addGroup(
                                        layout.createSequentialGroup()
                                        .addComponent(
                                                availablePluginsLabel)
                                        .addPreferredGap(
                                                LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(
                                                jScrollPane1,
                                                GroupLayout.PREFERRED_SIZE,
                                                129,
                                                GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(
                                                LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(
                                                selectedPluginsLabel)
                                        .addPreferredGap(
                                                LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(
                                                jScrollPane2,
                                                GroupLayout.PREFERRED_SIZE,
                                                GroupLayout.DEFAULT_SIZE,
                                                GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(
                                                LayoutStyle.ComponentPlacement.RELATED,
                                                7,
                                                Short.MAX_VALUE)
                                        .addGroup(
                                                layout.createParallelGroup(
                                                        GroupLayout.Alignment.BASELINE)
                                                .addComponent(
                                                        addOption)
                                                .addComponent(
                                                        removeOption))))
                        .addContainerGap()));
        
        pack();
    }
    
    private void lstUnselectedMouseClicked(MouseEvent evt) {
        switch (evt.getClickCount()) {
        case 1:
            scrollPane.setEnabled(false);
            addOption.setEnabled(true);
            removeOption.setEnabled(false);
            break;
        case 2:
            addOption.doClick();
            break;
        }
    }
    
    private void lstSelectedMouseClicked(MouseEvent evt) {
        switch (evt.getClickCount()) {
        case 1:
            addOption.setEnabled(false);
            removeOption.setEnabled(!selectedPlugins.isEmpty());
            T selectedPlugin = selectedPluginsList.getSelectedValue();
            if (selectedPlugin != null)
                setPropertySheet(selectedPlugin);
            break;
        case 2:
            removeOption.doClick();
            break;
        }
    }
    
    private void onAdd(ActionEvent evt) {
        T selectedPlugin = availablePluginsList.getSelectedValue();
        if (selectedPlugin != null) {
            selectedPlugin = selectedPlugin.newInstance();
            // Make sure that always a new panel UUID is generated, regardless
            // how the copy() method works.
            // TODO: panel selectable from properties?
            if (selectedPlugin instanceof Indicator)
                ((Indicator) selectedPlugin).setPanelId(UUID.randomUUID());
            selectedPlugins.addElement(selectedPlugin);
            setPropertySheet(selectedPlugin);
        }
    }
    
    private void onRemove(ActionEvent evt) {
        for (int i : selectedPluginsList.getSelectedIndices()) {
            selectedPlugins.remove(i);
            removeOption.setEnabled(false);
            scrollPane.setEnabled(false);
            unsetPropertySheet();
        }
    }
    
    private void onFinish(ActionEvent e) {
        applyChanges(e);
        fireWindowClosing();
    }
    
    private void applyChanges(ActionEvent e) {
        List<T> resultList = new ArrayList<>();
        Enumeration<T> iter = selectedPlugins.elements();
        while (iter.hasMoreElements())
            resultList.add(iter.nextElement());
        
        selectionHandler.accept(resultList);
    }
    
    private void onCancel(ActionEvent evt) {
        fireWindowClosing();
    }
    
    private JScrollPane jScrollPane1;
    private JScrollPane jScrollPane2;
    private JSeparator jSeparator1;
    private JLabel propertiesLabel;
    private JPanel scrollPane;
    
    
    /**
     * Sets a custom label for the available plugins list.
     * 
     * @param text
     *            the new label text
     */
    protected void setAvailablePluginsLabelText(String text) {
        availablePluginsLabel.setText(text);
    }
    
    /**
     * Sets a custom label for the selected plugins list.
     * 
     * @param text
     *            the new label text
     */
    protected void setSelectedPluginsLabelText(String text) {
        selectedPluginsLabel.setText(text);
    }
}
