package one.chartsy.charting.util;

import java.io.Serializable;

/// Wraps a mutable integer bit mask behind a small serializable helper object.
///
/// Chart, axis, legend, and scale implementations use this type to keep many boolean feature
/// switches in a compact form while still passing them around as a single mutable object.
public final class Flags implements Serializable {
    private int mask;
    
    /// Creates a flag set with all bits cleared.
    public Flags() {
    }
    
    /// Copies the raw bit mask from another flag set.
    public Flags(Flags other) {
        mask = other.mask;
    }
    
    /// Creates a flag set from an already encoded bit mask.
    public Flags(int mask) {
        this.mask = mask;
    }
    
    /// Tests whether any bit present in `flagMask` is currently enabled.
    public boolean getFlag(int flagMask) {
        return (mask & flagMask) != 0;
    }
    
    /// Sets or clears every bit present in `flagMask` while leaving all other bits unchanged.
    public void setFlag(int flagMask, boolean enabled) {
        if (enabled) {
            mask |= flagMask;
        } else {
            mask &= ~flagMask;
        }
    }
}
