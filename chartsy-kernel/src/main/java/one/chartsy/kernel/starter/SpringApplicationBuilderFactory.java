/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.starter;

import org.springframework.boot.builder.SpringApplicationBuilder;

@FunctionalInterface
public interface SpringApplicationBuilderFactory {

    SpringApplicationBuilder createSpringApplicationBuilder();
}
