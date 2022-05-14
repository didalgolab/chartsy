/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation.reporting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.ZoneId;
import java.time.ZoneOffset;

@Accessors(fluent = true)
@Getter
@AllArgsConstructor
public class StandardEquityInformation implements EquityInformation {

    private final double startingEquity;
    private final double totalEquityHigh;
    private final double totalEquityLow;
    private final double endingEquity;
    private final double maxDrawdown;
    private final double maxDrawdownPercent;
    private final double avgDrawdown;
    private final double avgDrawdownPercent;
    private final long totalEquityHighTime;
    private final long maxDrawdownTime;
    private final long maxDrawdownPercentTime;
    private final long longestDrawdownDuration;
    private final long startTime;
    private final long endTime;

    @Override
    public double years() {
        return years(ZoneOffset.UTC);
    }

    @Override
    public double years(ZoneId zone) {
        return EquityInformationSupport.instance().years(startTime(), endTime(), zone);
    }
}
