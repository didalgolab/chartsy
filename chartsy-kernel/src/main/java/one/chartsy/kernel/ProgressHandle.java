/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel;

public interface ProgressHandle {

    void start(int workTotal);

    void progress(String message, int workDone);

    void finish();
}
