package one.chartsy.concurrent;

import org.openide.util.Lookup;

import java.util.concurrent.Executor;

public final class DirectExecutor implements Executor {

    @Override
    public void execute(Runnable action) {
        action.run();
    }

    public static DirectExecutor instance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final DirectExecutor INSTANCE = createInstance();

        private static DirectExecutor createInstance() {
            DirectExecutor executor = Lookup.getDefault().lookup(DirectExecutor.class);
            return (executor != null)? executor : new DirectExecutor();
        }
    }

    public DirectExecutor() { }
}
