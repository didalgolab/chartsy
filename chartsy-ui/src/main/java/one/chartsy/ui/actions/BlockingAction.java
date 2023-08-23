/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.actions;

import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * The action that executed the given code in a background, and shows a blocking
 * wait dialog to the user until the execution of the background code finishes.
 * 
 * @author Mariusz Bernacki
 *
 */
public class BlockingAction extends AbstractAction {
    /** The background action job to be executed. */
    private final Runnable backgroundWork;
    
    
    public BlockingAction(String name, Runnable backgroundWork) {
        super(name);
        this.backgroundWork = backgroundWork;
    }
    
    @Override
    public void actionPerformed(ActionEvent evt) {
        SwingWorker<Void, Void> mySwingWorker = new SwingWorker<>() {
            
            @Override
            protected Void doInBackground() {
                // launch the background work
                backgroundWork.run();
                return null;
            }
        };
        
        Window owner = null;
        if (evt.getSource() instanceof Window)
            owner = (Window) evt.getSource();
        else if (evt.getSource() instanceof Component)
            owner = SwingUtilities.getWindowAncestor((Component) evt.getSource());

        JDialog dialog = new JDialog(owner, "Please wait...", ModalityType.APPLICATION_MODAL);
        mySwingWorker.addPropertyChangeListener(event -> {
            if (event.getPropertyName().equals("state") && event.getNewValue() == SwingWorker.StateValue.DONE)
                dialog.dispose();
        });
        mySwingWorker.execute();
        
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel(backgroundWork.toString()));
        panel.add(progressBar);
        panel.add(new JLabel());
        
        JOptionPane optionPane = new JOptionPane(panel, JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[] {}, null);
        
        dialog.setContentPane(optionPane);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}