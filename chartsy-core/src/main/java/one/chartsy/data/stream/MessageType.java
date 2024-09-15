/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.stream;

import java.util.function.BiConsumer;

public interface MessageType {

    String name();

    Class<?> handlerType();

    BiConsumer<Object, Message> handlerFunction();
}
