/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.boot;

import org.springframework.context.ApplicationContext;

public interface FrontEnd {

    ApplicationContext getApplicationContext();
}
