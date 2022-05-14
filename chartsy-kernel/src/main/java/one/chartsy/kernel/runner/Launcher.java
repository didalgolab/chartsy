/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.runner;

import java.nio.file.Path;

public interface Launcher {

    void launch(Path projectDir, String className) throws ClassNotFoundException, LaunchException;
}
