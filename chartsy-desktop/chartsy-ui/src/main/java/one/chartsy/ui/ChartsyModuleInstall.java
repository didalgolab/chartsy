/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui;

import one.chartsy.kernel.StartupMetrics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openide.modules.ModuleInstall;
import org.openide.windows.WindowManager;

import java.nio.file.Files;
import java.nio.file.Path;

public class ChartsyModuleInstall extends ModuleInstall {

    private static final Logger log = LogManager.getLogger(ChartsyModuleInstall.class);

    @Override
    public void restored() {
        StartupMetrics.mark("installer:start");
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            StartupMetrics.mark("ui:ready");
            StartupServices.prewarmFullStack();
        });
        StartupServices.prewarm();
        log.info("Scheduling background symbols warm-up");

        Path projectsDir;
        if (System.getProperty("netbeans.projects.dir") == null
                && System.getenv().containsKey("CHARTSY_PROJECTS_DIR")
                && Files.isDirectory(projectsDir = Path.of(System.getenv().get("CHARTSY_PROJECTS_DIR"))))
            System.setProperty("netbeans.projects.dir", projectsDir.toString());

        StartupMetrics.mark("installer:done");
    }
}
