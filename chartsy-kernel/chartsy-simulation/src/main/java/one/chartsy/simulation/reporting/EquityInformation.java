/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation.reporting;

import one.chartsy.trade.Account;
import one.chartsy.trade.BalanceState;
import one.chartsy.trade.data.Position;
import one.chartsy.trade.event.LegacyPositionValueChangeListener;

import java.time.ZoneId;

public interface EquityInformation {

    double startingEquity();

    double totalEquityHigh();

    double totalEquityLow();

    double endingEquity();

    double maxDrawdown();

    double maxDrawdownPercent();

    double avgDrawdown();

    double avgDrawdownPercent();

    long totalEquityHighTime();

    long maxDrawdownTime();

    long maxDrawdownPercentTime();

    long longestDrawdownDuration();

    long startTime();

    long endTime();

    double years();

    double years(ZoneId zone);

    static Builder builder(BalanceState initial) {
        return new StandardBuilder(initial);
    }

    interface Builder extends LegacyPositionValueChangeListener {

        void add(long time, double equity);

        EquityInformation build();
    }

    class StandardBuilder implements Builder {
        private double startingEquity;
        private double totalEquityHigh;
        private double totalEquityLow;
        private double currentEquity;
        private double maxDrawdown;
        private double maxDrawdownPercent;
        private double drawdownTotal;
        private double drawdownPercentTotal;
        private long longestDrawdownDuration;
        private long currentDrawdownStartTime;
        private long totalEquityHighTime;
        private long maxDrawdownTime;
        private long maxDrawdownPercentTime;
        private long startingTime;
        private long currentTime;
        private long dataPoints;

        public final boolean isEmpty() {
            return (dataPoints == 0);
        }

        public StandardBuilder(BalanceState initial) {
            this.startingEquity = this.totalEquityHigh = this.totalEquityLow = this.currentEquity = initial.getEquity();
        }

        @Override
        public void positionValueChanged(Account account, Position position) {
            add(position.getMarketTime(), account.getEquity());
        }

        @Override
        public void add(long time, double equity) {
            if (dataPoints++ == 0)
                startingTime = time;
            currentEquity = equity;
            currentTime = time;
            if (equity > totalEquityHigh) {
                totalEquityHigh = equity;
                totalEquityHighTime = time;
            }
            totalEquityLow = Math.min(totalEquityLow, equity);
            double drawdown = totalEquityHigh - equity;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
                maxDrawdownTime = time;
            }
            double drawdownPercent = 100.0*drawdown/totalEquityHigh;
            if (drawdownPercent > maxDrawdownPercent) {
                maxDrawdownPercent = drawdownPercent;
                maxDrawdownPercentTime = time;
            }
            if (drawdown >= 0.0)
                currentDrawdownStartTime = time;
            longestDrawdownDuration = Math.max(longestDrawdownDuration, time - currentDrawdownStartTime);
            drawdownTotal += drawdown;
            drawdownPercentTotal += drawdownPercent;
        }

        @Override
        public StandardEquityInformation build() {
            boolean empty = isEmpty();
            return new StandardEquityInformation(
                    startingEquity,
                    totalEquityHigh,
                    totalEquityLow,
                    currentEquity,
                    maxDrawdown,
                    maxDrawdownPercent,
                    empty? 0: drawdownTotal/dataPoints,
                    empty? 0: drawdownPercentTotal/dataPoints,
                    totalEquityHighTime,
                    maxDrawdownTime,
                    maxDrawdownPercentTime,
                    longestDrawdownDuration,
                    startingTime,
                    currentTime
            );
        }
    }
}
