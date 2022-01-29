package one.chartsy.ui;

import one.chartsy.frontend.FrontEnd;
import one.chartsy.kernel.Kernel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openide.modules.OnStart;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

@OnStart
public class Installer implements Runnable {

    private final Logger log = LogManager.getLogger(getClass());
    private static volatile Kernel kernel;
    private static volatile FrontEnd frontEnd;

    @Override
    public void run() {
        if (kernel == null) {
            synchronized (Installer.class) {
                if (kernel == null)
                    kernel = Lookup.getDefault().lookup(Kernel.class);
            }
        }
//        ForkJoinPool.commonPool().execute(() -> {
            try {
                log.info("Installing FrontEnd");
                frontEnd = Lookup.getDefault().lookup(FrontEnd.class);
            } catch (Exception e) {
                log.fatal("FrontEnd installation error", e);
            }
//        });
    }

    @ServiceProvider(service = Kernel.class)
    public static class DefaultKernel extends Kernel {

        public DefaultKernel() { }

        public void close() {
            // do nothing
        }
    }
}
