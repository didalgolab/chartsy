/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import java.awt.AWTEvent;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * The base class for all interactors that can be attached to a manager view.
 * 
 * @author Mariusz Bernacki
 *
 */
public class OrganizedViewInteractor {
    /** The organized view currently attached to this interactor. */
    private OrganizedView organizedView;
    
    
    /**
     * Returns the organized view currently attached to this interactor.
     * <p>
     * The method returns view attached by the framework to this interactor with
     * the {@link #attach(OrganizedView)} method. Returns {@code null} if no
     * view has been attached yet, or the previously attached view was detached
     * using the {@link #detach()} method.
     * 
     * @return the currently attached view, or {@code null} if none
     */
    public final OrganizedView getOrganizedView() {
        return organizedView;
    }
    
    /**
     * Returns the coordinate system of the view this interactor is attached to.
     * <p>
     * The method returns {@code null} if the interactor isn't attached to any
     * view.
     * 
     * @return the current coordinate system, or {@code null} if none available
     */
    public final CoordinateSystem getCoordinateSystem() {
        OrganizedView view = getOrganizedView();
        return (view == null)? null : view.getCoordinateSystem();
    }
    
    /**
     * Called by the framework when the interactor is attached to the
     * {@code view}.
     * <p>
     * The user should not call this method directly, unless developing
     * customized view implementations.
     * 
     * @param view
     *            the organized view
     * @see #detach()
     * @see #getOrganizedView()
     */
    protected void attach(OrganizedView view) {
        //		OrganizedView oldView = organizedView;
        //		if (oldView != null)
        //			oldView.detach();
        organizedView = view;
    }
    
    /**
     * Called by the framework when the interactor is detached from the view.
     * <p>
     * The method does nothing if the interactor isn't attached to any view
     * currently.
     * 
     * @see #attach(OrganizedView)
     */
    protected void detach() {
        organizedView = null;
    }
    
    //	static enum EventType {
    //		COMPONENT_EVENT
    //	}
    //	
    //	private EnumMap<EventType, EventListenerSupport<? extends EventListener>> listenerMap = new EnumMap<>(EventType.class);
    //
    //	public void addComponentListener(ComponentListener listener) {
    //		EventType key = EventType.COMPONENT_EVENT;
    //		listenerMap.computeIfAbsent(key, k -> EventListenerSupport.create(ComponentListener.class))
    //		.addListener(listener);
    //	}
    
    protected void processEvent(AWTEvent e) {
        if (e instanceof FocusEvent) {
            processFocusEvent((FocusEvent)e);
            
        } else if (e instanceof MouseEvent) {
            switch(e.getID()) {
            case MouseEvent.MOUSE_PRESSED:
            case MouseEvent.MOUSE_RELEASED:
            case MouseEvent.MOUSE_CLICKED:
            case MouseEvent.MOUSE_ENTERED:
            case MouseEvent.MOUSE_EXITED:
                processMouseEvent((MouseEvent) e);
                break;
            case MouseEvent.MOUSE_MOVED:
            case MouseEvent.MOUSE_DRAGGED:
                processMouseMotionEvent((MouseEvent) e);
                break;
            case MouseEvent.MOUSE_WHEEL:
                processMouseWheelEvent((MouseWheelEvent) e);
                break;
            }
        } else if (e instanceof KeyEvent) {
            processKeyEvent((KeyEvent) e);
        } else if (e instanceof ComponentEvent) {
            processComponentEvent((ComponentEvent) e);
        } else if (e instanceof InputMethodEvent) {
            processInputMethodEvent((InputMethodEvent) e);
        } else if (e instanceof HierarchyEvent) {
            switch (e.getID()) {
                case HierarchyEvent.HIERARCHY_CHANGED -> processHierarchyEvent((HierarchyEvent) e);
                case HierarchyEvent.ANCESTOR_MOVED,
                     HierarchyEvent.ANCESTOR_RESIZED -> processHierarchyBoundsEvent((HierarchyEvent) e);
            }
        }
    }
    
    protected void processComponentEvent(ComponentEvent e) {
    }
    
    protected void processFocusEvent(FocusEvent e) {
    }
    
    protected void processKeyEvent(KeyEvent e) {
    }
    
    public void processMouseEvent(MouseEvent e) {
    }
    
    public void processMouseMotionEvent(MouseEvent e) {
    }
    
    protected void processMouseWheelEvent(MouseWheelEvent e) {
    }
    
    protected void processInputMethodEvent(InputMethodEvent e) {
    }
    
    protected void processHierarchyEvent(HierarchyEvent e) {
    }
    
    protected void processHierarchyBoundsEvent(HierarchyEvent e) {
    }
}
