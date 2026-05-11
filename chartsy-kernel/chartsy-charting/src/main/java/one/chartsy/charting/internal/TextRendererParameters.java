package one.chartsy.charting.internal;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.io.Serializable;
import java.util.Objects;

import one.chartsy.charting.LabelRenderer;

/// Captures the subset of [TextRenderer] state that determines text layout, wrapping, and
/// rotation.
///
/// [LabelRenderer] uses instances of this type as mutable probe objects and cache keys for
/// reusable [TextRenderer] instances. Equality and hash codes intentionally depend only on the
/// stored text, font, antialiasing flag, auto-wrapping flag, wrapping width, and rotation; extra
/// presentation flags such as outline and alignment live in [TextRenderer] and are excluded.
///
/// Instances are mutable and not thread-safe. Do not mutate an instance while it is serving as a
/// key in a hash-based collection.
public class TextRendererParameters implements Serializable {
    String text;
    Font font;
    boolean antiAliased;
    boolean autoWrapping;
    float wrappingWidth;
    double rotation;

    /// Creates an empty parameter set with wrapping disabled and zero rotation.
    ///
    /// The initial state leaves both text and font unspecified. Consumers such as [LabelRenderer]
    /// typically populate all fields before using the instance for cache lookup.
    public TextRendererParameters() {
        autoWrapping = false;
        wrappingWidth = 0.0f;
    }

    /// Returns whether the supplied object describes the same layout-affecting text parameters.
    ///
    /// Two instances are equal only when all six cached properties match. Subclass-specific
    /// presentation state that does not live in this base class is excluded.
    @Override
    public boolean equals(Object obj) {
        return obj instanceof TextRendererParameters that
                && Objects.equals(text, that.text)
                && Objects.equals(font, that.font)
                && antiAliased == that.antiAliased
                && autoWrapping == that.autoWrapping
                && wrappingWidth == that.wrappingWidth
                && rotation == that.rotation;
    }

    /// Returns a hash code based on the same properties used by [#equals(Object)].
    ///
    /// The implementation normalizes signed zero for the floating-point fields so the hash remains
    /// consistent with the equality contract.
    @Override
    public int hashCode() {
        int result = Objects.hash(text, font, antiAliased, autoWrapping);
        result = 31 * result + hashFloat(wrappingWidth);
        result = 31 * result + hashDouble(rotation);
        return result;
    }

    private static int hashFloat(float value) {
        return value == 0.0f ? 0 : Float.floatToIntBits(value);
    }

    private static int hashDouble(double value) {
        long bits = value == 0.0d ? 0L : Double.doubleToLongBits(value);
        return Long.hashCode(bits);
    }

    /// Sets whether text layout should use antialiased font metrics.
    ///
    /// [TextRenderer] feeds this flag into the [FontRenderContext] it uses to shape
    /// text, so toggling it can change both appearance and measured bounds.
    ///
    /// @param antiAliased `true` to measure and draw text with antialiasing-aware metrics
    public void setAntiAliased(boolean antiAliased) {
        this.antiAliased = antiAliased;
    }

    /// Sets whether automatic line breaking may occur when a positive wrapping width is available.
    ///
    /// The flag has no practical effect until [#setWrappingWidth(float)] supplies a width greater
    /// than `0`.
    ///
    /// @param autoWrapping `true` to allow automatic wrapping
    public void setAutoWrapping(boolean autoWrapping) {
        this.autoWrapping = autoWrapping;
    }

    /// Sets the explicit font retained in this parameter set.
    ///
    /// `null` leaves font choice to the consumer. [TextRenderer] interprets that state as a
    /// request to fall back to the current label font.
    ///
    /// @param font explicit font to cache with these parameters, or `null` for consumer defaults
    public void setFont(Font font) {
        this.font = font;
    }

    /// Sets the text rotation angle in degrees.
    ///
    /// @param rotation rotation angle in degrees included in this parameter set
    public void setRotation(double rotation) {
        this.rotation = rotation;
    }

    /// Sets the source text retained in this parameter set.
    ///
    /// This base class stores the reference as-is and allows `null`. [TextRenderer] narrows the
    /// contract and rejects `null` text.
    ///
    /// @param text source text to associate with this parameter set
    public void setText(String text) {
        this.text = text;
    }

    /// Sets the maximum line width used for automatic wrapping.
    ///
    /// Non-positive values disable width-based wrapping even when [#setAutoWrapping(boolean)] is
    /// enabled.
    ///
    /// @param wrappingWidth maximum width available to the wrapping algorithm
    public void setWrappingWidth(float wrappingWidth) {
        this.wrappingWidth = wrappingWidth;
    }
}
