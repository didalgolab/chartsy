/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import org.openide.util.Lookup;

import java.nio.file.Files;
import java.nio.file.Path;

public record Workspace(Path path) {

    public static Workspace current() {
        return LazyHolder.CURRENT;
    }

    private static final class LazyHolder {

        private static final Workspace CURRENT;
        static {
            var workspace = Lookup.getDefault().lookup(Workspace.class);
            if (workspace == null) {
                var workspaceDir = System.getProperty("chartsy.home");
                if (workspaceDir == null)
                    workspaceDir = System.getProperty("user.home");

                var workspacePath = Path.of(workspaceDir).toAbsolutePath();
                if (!Files.isDirectory(workspacePath))
                    throw new InternalError("Invalid workspace dir: " + workspacePath);

                workspacePath = workspacePath.resolve(".chartsy");
                workspace = new Workspace(workspacePath);
            }
            CURRENT = workspace;
        }
    }
}
