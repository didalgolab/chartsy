/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel;

public interface ExplorationListener {

    void explorationFragmentCreated(ExplorationFragment next);

    /** Discards previously published rows when an online freshness reference advances. */
    default void explorationResultsReset() { }

    /** Reports a scan phase or its resolved reference; implementations may be called off the EDT. */
    default void explorationStatusChanged(String status) { }

    /** Marks any published rows as incomplete; successful completion is not fired afterward. */
    default void explorationFailed(Throwable failure) { }

    void explorationFinished();
}
