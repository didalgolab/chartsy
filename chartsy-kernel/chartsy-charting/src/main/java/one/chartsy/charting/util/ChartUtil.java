package one.chartsy.charting.util;

import java.util.StringTokenizer;

/// Provides legacy argument-validation and runtime-version helpers used by the charting module.
///
/// The current codebase uses this class for two unrelated compatibility tasks:
/// - rejecting `null` arguments with an [IllegalArgumentException] in older public APIs
/// - feature-gating `TextRenderer` behavior against the running JVM's parsed `java.version`
///
/// [#getJavaVersion()] lazily caches a three-element `int[]` and returns that live array on every
/// call. The parser reads at most the first three dot-separated components and strips `_...` or
/// `-...` suffixes from the third component.
/// TODO: remove
public class ChartUtil {
    private static int[] javaVersion = null;
    
    /// Rejects a required parameter with the legacy exception type expected by this module.
    ///
    /// `Chart` and related APIs use this helper instead of the standard `requireNonNull` null check
    /// so callers receive an [IllegalArgumentException] whose message names the offending parameter.
    public static void checkNullParam(String name, Object value) {
        if (value == null)
            throw new IllegalArgumentException(
                    "Parameter <" + name + "> cannot be null.");
    }
    
    /// Compares the current JVM version against a legacy `major.minor.patch` triple.
    ///
    /// Positive results mean the running JVM is considered newer, negative results mean older, and
    /// `0` means equivalent for the comparison rules implemented here.
    ///
    /// This helper exists for old `1.4` versus `1.5` rendering feature gates in
    /// `TextRenderer`; it should not be treated as a general semantic-version comparator.
    public static int compareToJavaVersion(int major, int minor, int patch) {
        int[] version = getJavaVersion();
        if (major != version[0])
            return version[0] - major;
        if (minor == version[2])
            return version[2] - patch;
        return version[1] - minor;
    }
    
    /// Returns the lazily parsed runtime version as `{major, minor, patch}`.
    ///
    /// Missing components remain `0`. The returned array is the cached backing store used by this
    /// class, so callers should treat it as read-only.
    public static int[] getJavaVersion() {
        if (javaVersion == null) {
            int[] version = new int[3];
            javaVersion = version;
            try {
                StringTokenizer tokens = new StringTokenizer(System.getProperty("java.version"), ".");
                int part = 0;
                while (tokens.hasMoreTokens() && part < 3) {
                    String token = tokens.nextToken();
                    if (part == 2) {
                        int separator = token.indexOf(95);
                        if (separator >= 0)
                            token = token.substring(0, separator);
                        separator = token.indexOf(45);
                        if (separator >= 0)
                            token = token.substring(0, separator);
                    }
                    version[part++] = Integer.parseInt(token);
                }
            } catch (SecurityException ignored) {
            }
        }
        return javaVersion;
    }
    
    private ChartUtil() {
    }
}
