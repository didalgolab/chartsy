/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel;

public interface ExplorationListener {

    void explorationFragmentCreated(ExplorationFragment next);

    void explorationFinished();
}
