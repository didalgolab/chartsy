/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core;

/**
 * Denotes a refreshable element whose content or appearance is able to change
 * on demand.
 * 
 * @author Mariusz Bernacki
 */
public interface Refreshable {
    
    void refresh();

}