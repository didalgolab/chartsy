/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * Indicator and overlay chooser dialog.
 *
 * @author Mariusz Bernacki
 */
public class IndicatorChooserDialog extends JDialog {
    @Serial
    private static final long serialVersionUID = -8778936066219858851L;
    private static final Dimension MINIMUM_DIALOG_SIZE = new Dimension(1040, 660);

    @FunctionalInterface
    public interface SelectionHandler {
        void accept(ChartPluginSelection selection);
    }

    private final Component locationAnchor;
    private final SelectionHandler selectionHandler;
    private final IndicatorChooserPanel chooserPanel = new IndicatorChooserPanel();
    private final JButton applyOption = new JButton();
    private final JButton cancelOption = new JButton();
    private final JButton finishOption = new JButton();

    public IndicatorChooserDialog(Component locationAnchor, SelectionHandler selectionHandler) {
        super(resolveOwner(locationAnchor));
        this.locationAnchor = locationAnchor;
        this.selectionHandler = selectionHandler;
        setTitle(NbBundle.getMessage(IndicatorChooserDialog.class, "IChooser.title"));
        setModalityType(ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        initComponents();
        registerKeyboardActions();
        registerWindowActions();
    }

    protected void registerKeyboardActions() {
        getRootPane().registerKeyboardAction(__ -> fireWindowClosing(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().setDefaultButton(finishOption);
    }

    protected void registerWindowActions() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                // no-op
            }
        });
    }

    protected void fireWindowClosing() {
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

    public void initForm(Collection<? extends Indicator> allIndicators, Collection<? extends Indicator> selectedIndicators) {
        initForm(allIndicators, selectedIndicators, List.of(), List.of());
    }

    public void initForm(Collection<? extends Indicator> allIndicators,
                         Collection<? extends Indicator> selectedIndicators,
                         Collection<? extends Overlay> allOverlays,
                         Collection<? extends Overlay> selectedOverlays) {
        chooserPanel.initForm(allIndicators, selectedIndicators, allOverlays, selectedOverlays);
        pack();
        applyInitialSizeConstraints();
        positionWithinScreenBounds();
    }

    private void initComponents() {
        applyOption.setText(NbBundle.getMessage(IndicatorChooserDialog.class, "ChPChooser.btnApply.text"));
        applyOption.addActionListener(this::applyChanges);

        cancelOption.setText(NbBundle.getMessage(IndicatorChooserDialog.class, "ChPChooser.btnCancel.text"));
        cancelOption.addActionListener(this::onCancel);

        finishOption.setText(NbBundle.getMessage(IndicatorChooserDialog.class, "ChPChooser.btnOk.text"));
        finishOption.addActionListener(this::onFinish);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottomPanel.add(finishOption);
        bottomPanel.add(applyOption);
        bottomPanel.add(cancelOption);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.add(chooserPanel, BorderLayout.CENTER);
        content.add(bottomPanel, BorderLayout.SOUTH);
        content.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 12, 12));

        setContentPane(content);
    }

    private void onFinish(ActionEvent event) {
        applyChanges(event);
        fireWindowClosing();
    }

    private void applyChanges(ActionEvent event) {
        selectionHandler.accept(chooserPanel.getSelection());
    }

    private void onCancel(ActionEvent event) {
        fireWindowClosing();
    }

    private void applyInitialSizeConstraints() {
        Rectangle usableBounds = getUsableScreenBounds(locationAnchor);
        Dimension minimumSize = clampToBounds(MINIMUM_DIALOG_SIZE, usableBounds);
        setMinimumSize(minimumSize);

        int width = Math.max(getWidth(), minimumSize.width);
        int height = Math.max(getHeight(), minimumSize.height);
        setSize(Math.min(width, usableBounds.width), Math.min(height, usableBounds.height));
    }

    private void positionWithinScreenBounds() {
        Rectangle usableBounds = getUsableScreenBounds(locationAnchor);
        Rectangle referenceBounds = getVisibleReferenceBounds();
        int x = usableBounds.x + Math.max(0, (usableBounds.width - getWidth()) / 2);
        int y = usableBounds.y + Math.max(0, (usableBounds.height - getHeight()) / 2);
        if (referenceBounds != null) {
            x = referenceBounds.x + (referenceBounds.width - getWidth()) / 2;
            y = referenceBounds.y + (referenceBounds.height - getHeight()) / 2;
        }

        int maxX = usableBounds.x + Math.max(0, usableBounds.width - getWidth());
        int maxY = usableBounds.y + Math.max(0, usableBounds.height - getHeight());
        setLocation(Math.max(usableBounds.x, Math.min(x, maxX)),
                Math.max(usableBounds.y, Math.min(y, maxY)));
    }

    private Rectangle getVisibleReferenceBounds() {
        Component reference = getVisibleReferenceComponent();
        if (reference == null)
            return null;
        try {
            return new Rectangle(reference.getLocationOnScreen(), reference.getSize());
        } catch (IllegalComponentStateException ex) {
            return null;
        }
    }

    private Component getVisibleReferenceComponent() {
        if (locationAnchor != null && locationAnchor.isShowing())
            return locationAnchor;
        Window owner = getOwner();
        return owner != null && owner.isShowing() ? owner : null;
    }

    private static Dimension clampToBounds(Dimension dimension, Rectangle bounds) {
        return new Dimension(Math.min(dimension.width, bounds.width), Math.min(dimension.height, bounds.height));
    }

    private static Rectangle getUsableScreenBounds(Component anchor) {
        GraphicsConfiguration configuration = anchor != null ? anchor.getGraphicsConfiguration() : null;
        if (configuration == null) {
            GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
            configuration = environment.getDefaultScreenDevice().getDefaultConfiguration();
        }

        Rectangle bounds = new Rectangle(configuration.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        return bounds;
    }

    private static Window resolveOwner(Component locationAnchor) {
        Window owner = locationAnchor != null ? SwingUtilities.getWindowAncestor(locationAnchor) : null;
        if (owner != null)
            return owner;
        try {
            return WindowManager.getDefault().getMainWindow();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}


