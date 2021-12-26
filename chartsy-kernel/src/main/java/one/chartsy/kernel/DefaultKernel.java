package one.chartsy.kernel;

import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = Kernel.class)
public class DefaultKernel extends Kernel {

    public DefaultKernel() { }

    public void close() {
        // do nothing
    }
}
