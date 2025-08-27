/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.service.proxy;

import one.chartsy.Openable;
import one.chartsy.data.stream.Message;

public interface ServiceProxy extends Openable {

    boolean offer(Message message);
}
