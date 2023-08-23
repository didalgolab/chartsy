/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.exploration.ui;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Table;

@Getter
@Setter
@Table(name = "ONE_EXPLORATION_CONFIGURATIONS")
public class ExplorationConfigurationData {
    private Long id;

}
