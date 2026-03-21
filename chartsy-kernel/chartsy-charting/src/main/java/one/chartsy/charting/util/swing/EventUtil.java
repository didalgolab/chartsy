package one.chartsy.charting.util.swing;

import java.awt.event.InputEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/// Normalizes legacy AWT modifier masks to their extended-mask equivalents.
///
/// `ChartInteractor` and `ChartZoomInteractor` use this helper when they cache event masks for
/// matching against incoming mouse events. The returned mask keeps the extended `*_DOWN_MASK`
/// values and clears the legacy low-bit `*_MASK` values that would otherwise make old and new
/// modifier APIs disagree.
///
/// Ambiguous legacy bits for `ALT_MASK` and `META_MASK` are still expanded, but the method logs a
/// warning because those values also overlap with `BUTTON2_MASK` and `BUTTON3_MASK`.
@SuppressWarnings("deprecation")
public final class EventUtil {
    private static final Logger LOGGER = Logger.getLogger(EventUtil.class.getName());
    private static final int LEGACY_LOW_MASKS = InputEvent.SHIFT_MASK
            | InputEvent.CTRL_MASK
            | InputEvent.META_MASK
            | InputEvent.ALT_MASK
            | InputEvent.BUTTON1_MASK;
    
    /// Returns a best-effort description of the setter that supplied an ambiguous event mask.
    ///
    /// When several consecutive stack frames come from the same class, the outermost frame in that
    /// class is used so warnings point at the public setter rather than an internal helper.
    private static String callerDescription() {
        String caller = "setEventMask";
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        if (stackTrace.length > 2) {
            String callerClass = stackTrace[2].getClassName();
            int callerIndex = 2;
            while (callerIndex + 1 < stackTrace.length
                    && stackTrace[callerIndex + 1].getClassName().equals(callerClass))
                callerIndex++;
            caller = stackTrace[callerIndex].getClassName() + "." + stackTrace[callerIndex].getMethodName();
        }
        return caller;
    }
    
    /// Converts a legacy modifier mask to the extended-mask form used by current AWT events.
    ///
    /// The result is suitable for exact comparison with `getModifiersEx()` values. Low-order legacy
    /// bits such as `SHIFT_MASK` and `BUTTON1_MASK` are removed after their corresponding
    /// `*_DOWN_MASK` flags are added. `ALT_GRAPH_MASK` remains because it does not overlap the same
    /// ambiguous low-order bit range.
    ///
    /// @param mask legacy modifier mask, typically composed from `InputEvent.*_MASK` constants
    /// @return normalized extended modifier mask for exact event matching
    public static int convertModifiersMask(int mask) {
        int convertedMask = mask;
        if ((convertedMask & InputEvent.SHIFT_MASK) != 0)
            convertedMask |= InputEvent.SHIFT_DOWN_MASK;
        if ((convertedMask & InputEvent.CTRL_MASK) != 0)
            convertedMask |= InputEvent.CTRL_DOWN_MASK;
        if ((convertedMask & InputEvent.ALT_GRAPH_MASK) != 0)
            convertedMask |= InputEvent.ALT_GRAPH_DOWN_MASK;
        if ((convertedMask & InputEvent.BUTTON1_MASK) != 0)
            convertedMask |= InputEvent.BUTTON1_DOWN_MASK;
        if ((convertedMask & InputEvent.ALT_MASK) != 0) {
            convertedMask |= InputEvent.ALT_DOWN_MASK;
            LOGGER.log(Level.WARNING,
                    EventUtil.callerDescription() + ": mask is ambiguous. Please use " +
                            "InputEvent.BUTTON2_DOWN_MASK instead of InputEvent.BUTTON2_MASK, " +
                            "and InputEvent.ALT_DOWN_MASK instead of InputEvent.ALT_MASK.");
        }
        if ((convertedMask & InputEvent.META_MASK) != 0) {
            convertedMask |= InputEvent.META_DOWN_MASK;
            LOGGER.log(Level.WARNING,
                    EventUtil.callerDescription() + ": mask is ambiguous. Please use " +
                            "InputEvent.BUTTON3_DOWN_MASK instead of InputEvent.BUTTON3_MASK, " +
                            "and InputEvent.META_DOWN_MASK instead of InputEvent.META_MASK.");
        }
        return convertedMask & ~LEGACY_LOW_MASKS;
    }
    
    private EventUtil() {
    }
}
