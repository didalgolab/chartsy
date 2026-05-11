package one.chartsy.charting.util;

import java.lang.reflect.Array;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/// Legacy reflection helpers for array-class lookup and class-name-based hierarchy checks.
///
/// The subtype predicates in this utility are intentionally narrow: they walk only the superclass
/// chain unless the dedicated [#instanceOfInterface(Object, String)] helper is used. Even that
/// interface helper inspects only the interfaces returned by the runtime class's
/// [Class#getInterfaces()] call and does not recursively traverse the full type graph.
public final class Reflection {
    private static final ConcurrentMap<Class<?>, Class<?>> ARRAY_TYPES = new ConcurrentHashMap<>();

    private Reflection() {
    }

    private static Class<?> createArrayType(Class<?> componentType) {
        return Array.newInstance(componentType, 0).getClass();
    }

    /// Returns the JVM array class for the supplied component type.
    ///
    /// The result is cached and works for both reference and primitive component types.
    ///
    /// @param componentType component type to wrap in a one-dimensional array type
    /// @return array class whose component type is `componentType`
    /// @throws NullPointerException if `componentType` is `null`
    public static Class<?> arrayType(Class<?> componentType) {
        Objects.requireNonNull(componentType, "componentType");
        return ARRAY_TYPES.computeIfAbsent(componentType, Reflection::createArrayType);
    }

    /// Returns whether the runtime class of the supplied object matches the named class or one of
    /// its superclasses.
    ///
    /// This method does not consult implemented interfaces. For interface-name checks use
    /// [#instanceOfInterface(Object, String)] instead.
    ///
    /// @param value object whose runtime class should be tested
    /// @param className fully qualified binary class name to look for in the superclass chain
    /// @return `true` if `value` is non-null and its runtime class matches `className` through
    ///     direct equality or superclass traversal
    public static boolean instanceOf(Object value, String className) {
        if (value == null) {
            return false;
        }
        return isSubclassOf(value.getClass(), className);
    }

    /// Returns whether the runtime class directly reports an implemented interface with the given
    /// name.
    ///
    /// This is a shallow check over [Class#getInterfaces()]. It does not recurse into parent
    /// interfaces and does not walk the superclass chain.
    ///
    /// @param value object whose runtime class should be tested
    /// @param interfaceName fully qualified binary interface name to search for
    /// @return `true` if `value` is non-null and its runtime class directly reports an interface
    ///     named `interfaceName`
    public static boolean instanceOfInterface(Object value, String interfaceName) {
        if (value == null) {
            return false;
        }
        for (Class<?> implementedInterface : value.getClass().getInterfaces()) {
            if (implementedInterface.getName().equals(interfaceName)) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether the supplied class is the same as, or a subclass of, another class.
    ///
    /// The check follows only the superclass chain and therefore returns `false` for interface
    /// assignability unless the two class objects are exactly the same interface.
    ///
    /// @param type class to test
    /// @param superType target class to look for in the superclass chain
    /// @return `true` if `type` is `superType` or extends it
    public static boolean isSubclassOf(Class<?> type, Class<?> superType) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current == superType) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether the supplied class matches the named class or one of its superclasses.
    ///
    /// As with [#isSubclassOf(Class, Class)], this helper ignores interface assignability and
    /// compares only the class-name chain reported by successive [Class#getSuperclass()] calls.
    ///
    /// @param type class to test
    /// @param className fully qualified binary class name to look for
    /// @return `true` if `type` matches `className` directly or through superclass traversal
    public static boolean isSubclassOf(Class<?> type, String className) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }
}
