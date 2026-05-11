package one.chartsy.charting.util.text;

import java.awt.ComponentOrientation;

/// Adds and removes Unicode bidi control characters around chart-managed text.
///
/// Chart, legend, scale, annotation, and tooltip code use this utility to stabilize mixed-direction
/// labels before handing them to Swing text rendering. The helpers operate conservatively:
/// they infer direction from an explicit base-direction override when available, otherwise from the
/// first strong directional character in the text and finally from the owning component's
/// [ComponentOrientation].
///
/// The low-level normalization helpers strip at most one leading and one trailing control mark that
/// this utility itself may have inserted. That keeps repeated normalization idempotent without
/// trying to sanitize arbitrary bidi markup supplied by callers.
public final class BidiUtil {
    private static final int EXPLICIT_LTR = 516;
    private static final int EXPLICIT_RTL = 520;

    /// Unicode left-to-right mark.
    public static final String LRM = "\u200e";
    /// Unicode right-to-left mark.
    public static final String RLM = "\u200f";
    /// Unicode left-to-right embedding mark.
    public static final String LRE = "\u202a";
    /// Unicode right-to-left embedding mark.
    public static final String RLE = "\u202b";
    /// Unicode pop directional formatting mark.
    public static final String PDF = "\u202c";

    /// Detects whether `text` should be treated as right-to-left when no explicit base-direction
    /// override applies.
    ///
    /// The scan stops at the first strong left-to-right or right-to-left character. Neutral and
    /// weak directional characters are ignored. If no strong character is found, the component
    /// orientation decides the fallback direction.
    private static boolean isRTLByTextOrDefaults(String text, ComponentOrientation orientation) {
        if (text != null) {
            for (int index = 0; index < text.length(); index++) {
                byte directionality = Character.getDirectionality(text.charAt(index));
                if (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                        || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                    return true;
                }
                if (directionality == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                    return false;
                }
            }
        }
        return orientation != null && !orientation.isLeftToRight();
    }

    /// Resolves the effective text direction from the supplied base-direction constant.
    ///
    /// Only the explicit left-to-right and right-to-left constants short-circuit the text scan.
    /// All other accepted direction values fall back to [#isRTLByTextOrDefaults(String,
    /// ComponentOrientation)].
    private static boolean isRTL(String text, int baseTextDirection, ComponentOrientation orientation) {
        if (baseTextDirection == EXPLICIT_LTR)
            return false;
        if (baseTextDirection == EXPLICIT_RTL)
            return true;
        return BidiUtil.isRTLByTextOrDefaults(text, orientation);
    }

    /// Returns whether `text` starts with a bidi mark that this utility may insert.
    private static boolean hasLeadingBidiMark(String text) {
        return text != null && !text.isEmpty()
                && (text.startsWith(LRM) || text.startsWith(RLM) || text.startsWith(LRE) || text.startsWith(RLE));
    }

    /// Returns whether `text` ends with a bidi mark that this utility may append.
    private static boolean hasTrailingBidiMark(String text) {
        return text != null && !text.isEmpty()
                && (text.endsWith(LRM) || text.endsWith(RLM) || text.endsWith(PDF));
    }

    /// Returns `text` prefixed with a directional embedding that matches its resolved direction.
    ///
    /// This variant is meant for standalone rendered strings such as chart labels, where the
    /// caller wants a leading embedding mark but not the closing [#PDF] terminator. A `null`
    /// input is returned unchanged.
    ///
    /// When `normalizeExistingMarks` is `true`, the method first removes one leading and trailing
    /// mark previously inserted by this utility before adding the new prefix.
    public static String getCombinedString(String text, int baseTextDirection, ComponentOrientation orientation,
                                           boolean normalizeExistingMarks) {
        if (text == null)
            return null;
        boolean rtl = BidiUtil.isStringRTL(text, baseTextDirection, orientation);
        if (normalizeExistingMarks)
            return BidiUtil.getStringWithMarks(text, rtl, true, false, true);
        return (rtl ? RLE : LRE) + text;
    }

    /// Returns `text` wrapped in a directional embedding that can be safely embedded inside a
    /// larger surrounding string.
    ///
    /// Unlike [#getCombinedString(String, int, ComponentOrientation, boolean)], this overload also
    /// appends [#PDF], making the result safe to concatenate into a larger string such as the HTML
    /// tooltip fragments built by the chart info view. A `null` input is returned unchanged.
    ///
    /// When `normalizeExistingMarks` is `true`, the method first removes one leading and trailing
    /// mark previously inserted by this utility before adding the new wrapper.
    public static String getEmbeddableCombinedString(String text, int baseTextDirection,
                                                     ComponentOrientation orientation, boolean normalizeExistingMarks) {
        if (text == null)
            return null;
        boolean rtl = BidiUtil.isStringRTL(text, baseTextDirection, orientation);
        if (normalizeExistingMarks)
            return BidiUtil.getStringWithMarks(text, rtl, true, true, true);
        return (rtl ? RLE : LRE) + text + PDF;
    }

    /// Returns `text` with optional leading and trailing bidi control characters.
    ///
    /// The method always normalizes one previously inserted leading and trailing mark before
    /// applying the requested new wrapper, so repeated calls do not accumulate nested bidi
    /// controls. `useEmbeddingMarks` switches between directional marks ([#LRM]/[#RLM]) and
    /// directional embeddings ([#LRE]/[#RLE] with optional [#PDF]).
    public static String getStringWithMarks(String text, boolean rtl, boolean addLeadingMark, boolean addTrailingMark,
                                            boolean useEmbeddingMarks) {
        if (text == null || text.isEmpty())
            return text;

        String normalizedText = BidiUtil.removeMarksFromString(text);
        String leadingMark = addLeadingMark ? (!useEmbeddingMarks ? (rtl ? RLM : LRM) : (rtl ? RLE : LRE)) : "";
        String trailingMark = addTrailingMark ? (useEmbeddingMarks ? PDF : (rtl ? RLM : LRM)) : "";
        return leadingMark + normalizedText + trailingMark;
    }

    /// Returns whether `text` should be rendered as right-to-left.
    ///
    /// Explicit left-to-right and right-to-left base-direction constants win. Otherwise the method
    /// looks for the first strong directional character and finally falls back to `orientation`.
    public static boolean isStringRTL(String text, int baseTextDirection, ComponentOrientation orientation) {
        return BidiUtil.isRTL(text, baseTextDirection, orientation);
    }

    /// Removes one leading and one trailing bidi mark previously added by this utility.
    ///
    /// The method does not attempt to remove nested or internal bidi controls. It is intended only
    /// for undoing the single outer wrapper added by the helpers in this class. `null` and empty
    /// strings are returned unchanged.
    public static String removeMarksFromString(String text) {
        if (text == null || text.isEmpty())
            return text;

        int beginIndex = BidiUtil.hasLeadingBidiMark(text) ? 1 : 0;
        int endIndex = BidiUtil.hasTrailingBidiMark(text) ? text.length() - 1 : text.length();
        if (beginIndex >= endIndex)
            return "";
        return text.substring(beginIndex, endIndex);
    }

    private BidiUtil() {
    }
}
