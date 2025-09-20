/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.service;

import one.chartsy.util.OpenCloseable;

public interface Service extends OpenCloseable {

    String getId();
}
