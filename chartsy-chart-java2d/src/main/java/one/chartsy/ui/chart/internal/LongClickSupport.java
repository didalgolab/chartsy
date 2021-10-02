package one.chartsy.ui.chart.internal;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import one.chartsy.commons.event.ListenerList;

public class LongClickSupport implements ChangeListener, PropertyChangeListener, ActionListener {
    
    public static ListenerList<ActionListener> decorate(AbstractButton button) {
        LongClickSupport support = new LongClickSupport(button);
        button.addChangeListener(support);
        button.addPropertyChangeListener("model", support);
        return support.actionListeners;
    }
    
    /** The timer measuring how long the button is pressed. */
    private final Timer longClickTimer;
    /** The button being decorated by this {@code LongClickAction}. */
    private final AbstractButton button;
    /** Holds the last pressed state of the button. */
    private boolean pressed;
    /** Holds the list of registered action listeners. */
    private final ListenerList<ActionListener> actionListeners = ListenerList.of(ActionListener.class);
    
    private LongClickSupport(AbstractButton button) {
        longClickTimer = new Timer(1000, this);
        longClickTimer.setRepeats(false);
        this.button = button;
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        ButtonModel model = button.getModel();
        if (model.isPressed()) {
            if (!actionListeners.isEmpty())
                model.setArmed(false);
            actionListeners.fire().actionPerformed(new ActionEvent(button, e.getID(), e.getActionCommand(), e.getWhen(), e.getModifiers()));
        }
    }
    
    @Override
    public void stateChanged(ChangeEvent e) {
        AbstractButton button = (AbstractButton) e.getSource();
        ButtonModel model = button.getModel();
        
        // if the current state differs from the previous state
        if (model.isPressed() != pressed) {
            pressed = model.isPressed();
            
            if (longClickTimer.isRunning())
                longClickTimer.stop();
            if (pressed)
                longClickTimer.start();
        }
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent event) {
        ButtonModel oldModel = (ButtonModel) event.getOldValue();
        ButtonModel newModel = (ButtonModel) event.getNewValue();
        
        longClickTimer.stop();
        if (oldModel != null)
            oldModel.removeChangeListener(this);
        if (newModel != null) {
            newModel.addChangeListener(this);
            pressed = newModel.isPressed();
        }
    }
}
