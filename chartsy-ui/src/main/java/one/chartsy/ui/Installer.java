package one.chartsy.ui;

import one.chartsy.kernel.Kernel;
import org.openide.modules.OnStart;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

@OnStart
public class Installer implements Runnable {

    private static volatile Kernel kernel;

    @Override
    public void run() {
        if (kernel == null) {
            synchronized (Installer.class) {
                if (kernel == null)
                    kernel = Lookup.getDefault().lookup(Kernel.class);
            }
        }
    }

    @ServiceProvider(service = Kernel.class)
    public static class DefaultKernel extends Kernel {

        public DefaultKernel() { }

        public void close() {
            // do nothing
        }
    }
}
