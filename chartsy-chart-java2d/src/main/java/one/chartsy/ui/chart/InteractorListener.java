package one.chartsy.ui.chart;

/**
 * The interface to be implemented by an objects interested in listening for modifications
 * of an active interactor in the {@code OrganizedView}'s.
 * 
 * @author Mariusz Bernacki
 *
 */
public interface InteractorListener {
    
    /**
     * Called when the interactor of the organized view changed.
     * 
     * @param event the event
     */
    void interactorChanged(InteractorChangeEvent event);
    
}
