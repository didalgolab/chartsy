package one.chartsy.ui.swing;

import org.openide.nodes.Children;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public class NodeSupport {

    public static boolean areInitialized(Children children) {
        try {
            return (boolean) childrenInitializedCheck.invokeExact(children);
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    private static final MethodHandle childrenInitializedCheck;
    static {
        try {
            Method m = Children.class.getDeclaredMethod("isInitialized");
            m.setAccessible(true);
            childrenInitializedCheck = MethodHandles.lookup().unreflect(m);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException e) {
            e.printStackTrace();
            throw new InternalError(e);
        }
    }
}
