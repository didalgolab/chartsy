package one.chartsy.charting.util.swing;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.Serializable;

/// Optional normalizer for platform-specific mouse-event quirks.
///
/// `Chart.Area` installs the filter returned by [#createSystemEventFilter()] and runs both mouse
/// and mouse-motion events through [#filter(MouseEvent)] before dispatching them to chart logic.
/// Most platforms need no adjustment, so the factory returns `null`; the current special case is
/// Apple AWT.
public abstract class MouseEventFilter implements Serializable {
    private static final int BUTTON_DOWN_MASKS = InputEvent.BUTTON1_DOWN_MASK
            | InputEvent.BUTTON2_DOWN_MASK
            | InputEvent.BUTTON3_DOWN_MASK;
    
    /// Apple AWT workaround that restores drag semantics for Control-assisted mouse gestures.
    ///
    /// On that toolkit a Control-modified press can later surface as `MOUSE_MOVED` without the
    /// previously pressed button mask. This filter remembers the pressed button bits and rewrites
    /// the affected motion event into `MOUSE_DRAGGED` with those bits restored.
    private static final class AppleEventFilter extends MouseEventFilter {
        private transient int pressedButtonMask;
        
        AppleEventFilter() {
            pressedButtonMask = 0;
        }

        /// Recreates a drag event when Apple AWT drops button state from Control-assisted motion.
        ///
        /// The replacement event keeps the original source, timestamp, position, click count, and
        /// popup-trigger flag. Only the event id and modifier bits are adjusted.
        private MouseEvent createDraggedEvent(MouseEvent event) {
            return new MouseEvent((Component) event.getSource(), MouseEvent.MOUSE_DRAGGED, event.getWhen(),
                    event.getModifiersEx() | pressedButtonMask, event.getX(), event.getY(), event.getClickCount(),
                    event.isPopupTrigger());
        }
        
        @Override
        public MouseEvent filter(MouseEvent event) {
            switch (event.getID()) {
            case MouseEvent.MOUSE_PRESSED:
                if ((event.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == 0) {
                    pressedButtonMask = 0;
                    return event;
                }
                pressedButtonMask = event.getModifiersEx() & BUTTON_DOWN_MASKS;
                return event;
                
            case MouseEvent.MOUSE_MOVED:
                if (pressedButtonMask == 0)
                    return event;
                return createDraggedEvent(event);
                
            case MouseEvent.MOUSE_RELEASED:
            case MouseEvent.MOUSE_ENTERED:
            case MouseEvent.MOUSE_EXITED:
                pressedButtonMask = 0;
                return event;
                
            default:
                return event;
            }
        }
    }
    
    private static final boolean IS_APPLE_TOOLKIT = Toolkit.getDefaultToolkit().getClass().getName().startsWith("apple.awt.");
    
    /// Returns the platform-specific mouse-event filter needed by the current toolkit, or `null`
    /// when raw AWT events can be consumed directly.
    ///
    /// The returned filter is currently only needed on Apple AWT, where Control-assisted drags may
    /// lose their pressed-button state in intermediate motion events.
    public static MouseEventFilter createSystemEventFilter() {
        if (!IS_APPLE_TOOLKIT)
            return null;
        return new AppleEventFilter();
    }
    
    protected MouseEventFilter() {
    }
    
    /// Returns the original event or a replacement event normalized for the current platform.
    ///
    /// Implementations may keep transient gesture state between calls, so callers should reuse one
    /// filter instance for a stream of related mouse events.
    public abstract MouseEvent filter(MouseEvent event);
}
