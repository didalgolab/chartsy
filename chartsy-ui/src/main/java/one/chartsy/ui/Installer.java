/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui;

import one.chartsy.kernel.boot.FrontEnd;
import one.chartsy.kernel.Kernel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openide.modules.OnStart;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.nio.file.Files;
import java.nio.file.Path;

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
                    kernel = Kernel.getDefault();
            }
        }
        //ForkJoinPool.commonPool().execute(() -> {
            try {
                log.info("Installing FrontEnd");
                frontEnd = Lookup.getDefault().lookup(FrontEnd.class);
            } catch (Exception e) {
                log.fatal("FrontEnd installation error", e);
            }
        //});

        // change NetBeans default project directory to the Chartsy Codebase
        Path projectsDir;
        if (System.getProperty("netbeans.projects.dir") == null
                && System.getenv().containsKey("CHARTSY_PROJECTS_DIR")
                && Files.isDirectory(projectsDir = Path.of(System.getenv().get("CHARTSY_PROJECTS_DIR"))))
            System.setProperty("netbeans.projects.dir", projectsDir.toString());

    }

    @ServiceProvider(service = Kernel.class)
    public static class DefaultKernel extends Kernel {

        public DefaultKernel() { }

        public void close() {
            // do nothing
        }
    }
}
