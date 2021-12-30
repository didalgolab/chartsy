package one.chartsy.ui;

import one.chartsy.kernel.Kernel;
import org.openide.modules.OnStart;

@OnStart
public class Installer implements Runnable {

    private static volatile Kernel kernel;

    @Override
    public void run() {
        if (kernel == null) {
            synchronized (Installer.class) {
                if (kernel == null)
                    kernel = new Kernel();
            }
        }
    }
}
