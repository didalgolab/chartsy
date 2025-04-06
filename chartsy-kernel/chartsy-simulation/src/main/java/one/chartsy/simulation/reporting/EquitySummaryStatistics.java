/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.reporting;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;

/**
 * Collects and summarizes equity statistics (e.g., equity highs, lows, drawdowns, returns)
 * based on a series of equity data points. Additionally, it computes an incremental (streaming)
 * annual Sharpe and Sortino ratios for daily returns derived from the incoming timestamped
 * equity changes.
 *
 * <p><strong>Timestamp Granularity:</strong>
 * The {@code add} method converts incoming timestamps (in nanoseconds since the epoch) to a day
 * boundary. Whenever the day changes, a new daily return is computed using the ratio of the
 * current equity to the previous day's closing equity. These daily returns are pushed into
 * a streaming statistical accumulators.
 *
 * <p><strong>Annual Sharpe Ratio Calculation:</strong>
 * <pre>
 *   Sharpe(annual) = sqrt(252) * ((mean of daily returns - dailyRf) / stdDev of daily returns)
 * </pre>
 * where dailyRf = (annualRiskFreeRate / 252) for approximation.
 *
 * <p><strong>Annual Sortino Ratio Calculation:</strong>
 * <pre>
 *   Sortino(annual) = sqrt(252) * ((mean of daily returns - dailyRf) / downsideDeviation)
 * </pre>
 * where downsideDeviation is based on negative deviation from dailyRf only.
 *
 * <p><strong>Example Usage:</strong>
 * <pre>{@code
 * EquitySummaryStatistics stats = new EquitySummaryStatistics(10_000, 0.01);
 * // Suppose we process some equity updates with epochNanos timestamps
 * stats.add(10_500, 1687000000000000000L);
 * stats.add(10_200, 1687086400000000000L);
 * stats.add(11_000, 1687172800000000000L);
 * // ...
 *
 * // Once we are done adding data:
 * EquitySummaryStatisticsSnapshot finalStats = stats.createSnapshot();
 * System.out.println("Starting Equity:     " + finalStats.getStartingEquity());
 * System.out.println("Highest Equity:      " + finalStats.getTotalEquityHigh());
 * System.out.println("Lowest Equity:       " + finalStats.getTotalEquityLow());
 * System.out.println("Max Drawdown:        " + finalStats.getMaxDrawdown());
 * System.out.println("Average Drawdown:    " + finalStats.getAverageDrawdown());
 * System.out.println("Annual Sharpe Ratio: " + finalStats.getAnnualSharpeRatio());
 * // etc.
 * }</pre>
 *
 * @implNote This implementation is not thread-safe so synchronize it externally if used concurrently.
 *
 * @author Mariusz Bernacki
 */
public class EquitySummaryStatistics {
    private static final long NANOS_PER_DAY = 86_400_000_000_000L;

    /** The initial equity value when tracking started. */
    private final double startingEquity;
    /** The annualized risk-free rate (e.g., 0.01 for 1%). */
    private final double annualRiskFreeRate;
    /** The highest equity value reached in the data points. */
    private double totalEquityHigh;
    /** The lowest equity value reached in the data points. */
    private double totalEquityLow;
    /** The most recent equity value added. */
    private double endingEquity;
    /** The maximum absolute drawdown observed (largest drop from a previous equity high). */
    private double maxDrawdown;
    /** The maximum drawdown as a percentage of the highest equity. */
    private double maxDrawdownPercent;
    /** The cumulative sum of all observed drawdowns, used for computing the average drawdown. */
    private double drawdownTotals;
    /** The cumulative sum of all observed drawdowns as a percentage. */
    private double drawdownPercentTotals;
    /** The longest drawdown duration in nanoseconds. */
    private long longestDrawdownDuration;
    /** The current drawdown start time. */
    private long currentDrawdownStartTime;
    /** The timestamp corresponding to the highest recorded equity value. */
    private long totalEquityHighTime;
    /** The timestamp corresponding to the maximum drawdown observed. */
    private long maxDrawdownTime;
    /** The timestamp corresponding to the maximum drawdown percentage observed. */
    private long maxDrawdownPercentTime;
    /** Number of data points added so far. */
    private long dataPoints;
    /** The maintained day-based returns for incremental Sharpe ratio calculation. */
    private final SummaryStatistics dailyReturnStats = new SummaryStatistics();
    /** The sum of squared negative deviations from the daily risk-free rate. */
    private double dailyDownsideSumOfSquares;
    /** The last day we updated, in epoch days. */
    private long lastDay;
    /** The previous day's closing equity (for computing daily returns). */
    private double yesterdayClose;
    /** The regression used to track correlation(time, equity). */
    private final SimpleRegression timeEquityRegression = new SimpleRegression(true);
    /** The regression used to track correlation(time, log(equity)). */
    private final SimpleRegression timeLogEquityRegression = new SimpleRegression(true);

    /**
     * Constructs an {@code EquitySummaryStatistics} object initialized with the given equity.
     *
     * @param initialEquity the starting equity value
     * @param annualRiskFreeRate the assumed annual risk-free rate (e.g. 0.01 for 1%)
     */
    public EquitySummaryStatistics(double initialEquity, double annualRiskFreeRate) {
        this.startingEquity = initialEquity;
        this.totalEquityHigh = initialEquity;
        this.totalEquityLow = initialEquity;
        this.endingEquity = initialEquity;
        this.totalEquityHighTime = -1;
        this.maxDrawdownTime = -1;
        this.maxDrawdownPercentTime = -1;
        this.annualRiskFreeRate = annualRiskFreeRate;
        this.yesterdayClose = initialEquity;
    }

    /**
     * Adds an equity data point and updates summary statistics accordingly.
     *
     * <p>This method updates:
     * <ul>
     *   <li>Total equity high (if this equity is a new high)</li>
     *   <li>Total equity low (if this equity is a new low)</li>
     *   <li>Drawdown metrics (absolute and percentage)</li>
     *   <li>Average drawdown metrics (absolute and percentage)</li>
     *   <li>Ending equity</li>
     *   <li>Timestamp tracking for major events</li>
     *   <li>Daily return calculation for incremental Sharpe/Sortino ratio</li>
     *   <li>Correlation regressions (time vs equity, time vs log(equity))</li>
     * </ul>
     *
     * @param equity the new equity value to add
     * @param time the time associated with the equity value
     * @throws IllegalArgumentException if {@code equity} is negative
     */
    public void add(double equity, long time) {
        if (equity < 0) {
            throw new IllegalArgumentException("Equity cannot be negative: " + equity);
        }
        // If we've crossed into a new day, compute daily return.
        maybeAddDailyReturn(time);
        dataPoints++;
        endingEquity = equity;
        timeEquityRegression.addData(time, equity);
        if (equity > 0.0) {
            timeLogEquityRegression.addData(time, Math.log(equity));
        }
        if (equity > totalEquityHigh) {
            totalEquityHigh = equity;
            totalEquityHighTime = time;
        }
        if (equity < totalEquityLow) {
            totalEquityLow = equity;
        }
        double currentDrawdown = totalEquityHigh - equity;
        double currentDrawdownPercent = (totalEquityHigh == 0.0) ? 0.0 : (currentDrawdown / totalEquityHigh);
        if (currentDrawdown > maxDrawdown) {
            maxDrawdown = currentDrawdown;
            maxDrawdownTime = time;
        }
        if (currentDrawdownPercent > maxDrawdownPercent) {
            maxDrawdownPercent = currentDrawdownPercent;
            maxDrawdownPercentTime = time;
        }
        if (currentDrawdown <= 0.0) {
            currentDrawdownStartTime = time;
        }
        longestDrawdownDuration = Math.max(longestDrawdownDuration, time - currentDrawdownStartTime);
        drawdownTotals += currentDrawdown;
        drawdownPercentTotals += currentDrawdownPercent;
    }

    private void maybeAddDailyReturn(long time) {
        long currentDay = time / NANOS_PER_DAY;
        if (lastDay == 0) {
            lastDay = currentDay;
            return;
        }

        if (currentDay > lastDay) {
            double dailyReturn = getLastDayReturn();
            dailyReturnStats.addValue(dailyReturn);
            double dailyRf = annualRiskFreeRate / 252.0;
            double diff = dailyRf - dailyReturn;
            if (diff > 0.0) {
                dailyDownsideSumOfSquares += diff * diff;
            }
            lastDay = currentDay;
            yesterdayClose = endingEquity;
        }
    }

    /**
     * Returns the "most recent" "in-progress" daily return.
     *
     * @return the most recent daily return
     */
    public double getLastDayReturn() {
        return (endingEquity - yesterdayClose) / yesterdayClose;
    }

    /**
     * Returns the initial equity value when tracking started.
     *
     * @return the starting equity
     */
    public double getStartingEquity() {
        return startingEquity;
    }

    /**
     * Returns the highest equity value recorded.
     *
     * @return the highest equity value
     */
    public double getTotalEquityHigh() {
        return totalEquityHigh;
    }

    /**
     * Returns the lowest equity value recorded.
     *
     * @return the lowest equity value
     */
    public double getTotalEquityLow() {
        return totalEquityLow;
    }

    /**
     * Returns the most recent equity value that was added.
     *
     * @return the ending equity
     */
    public double getEndingEquity() {
        return endingEquity;
    }

    /**
     * Returns the net profit as the difference between the ending equity and the starting equity.
     *
     * @return the net profit
     */
    public double getNetProfit() {
        return endingEquity - startingEquity;
    }

    /**
     * Returns the maximum drawdown observed in absolute terms.
     *
     * @return the maximum drawdown in absolute terms
     */
    public double getMaxDrawdown() {
        return maxDrawdown;
    }

    /**
     * Returns the maximum drawdown as a percentage of the highest equity.
     *
     * @return the maximum drawdown percentage
     */
    public double getMaxDrawdownPercent() {
        return maxDrawdownPercent;
    }

    /**
     * Returns the average drawdown (absolute) across all recorded data points.
     *
     * @return the average drawdown
     */
    public double getAverageDrawdown() {
        var dataPoints = getDataPoints();
        return (dataPoints == 0) ? 0 : drawdownTotals / dataPoints;
    }

    /**
     * Returns the average drawdown as a percentage across all recorded data points.
     *
     * @return the average drawdown percentage
     */
    public double getAverageDrawdownPercent() {
        var dataPoints = getDataPoints();
        return (dataPoints == 0) ? 0 : drawdownPercentTotals / dataPoints;
    }

    /**
     * Gets the longest drawdown duration in nanoseconds.
     *
     * @return the longest drawdown duration
     */
    public long getLongestDrawdownDuration() {
        return longestDrawdownDuration;
    }

    /**
     * Gets the current drawdown start time in nanoseconds.
     *
     * @return the current drawdown start time
     */
    public long getCurrentDrawdownStartTime() {
        return currentDrawdownStartTime;
    }

    /**
     * Returns the time at which the highest equity was reached.
     *
     * @return the time of the highest equity
     *         or {@code -1} if none was recorded
     */
    public long getTotalEquityHighTime() {
        return totalEquityHighTime;
    }

    /**
     * Returns the time at which the maximum drawdown was recorded.
     *
     * @return the time of the max drawdown in absolute terms
     *         or {@code -1} if none was recorded
     */
    public long getMaxDrawdownTime() {
        return maxDrawdownTime;
    }

    /**
     * Returns the time at which the maximum drawdown percentage was recorded.
     *
     * @return the time of the max drawdown in percentage terms
     *         or {@code -1} if none was recorded
     */
    public long getMaxDrawdownPercentTime() {
        return maxDrawdownPercentTime;
    }

    /**
     * Returns the total number of data points that have been added.
     *
     * @return the total number of equity points processed
     */
    public long getDataPoints() {
        return dataPoints;
    }

    /**
     * Returns the average daily return computed from the accumulated daily returns.
     *
     * @return the average daily return, or {@code Double.NaN} if no daily returns have been recorded
     */
    public double getAverageDailyReturn() {
        long n = dailyReturnStats.getN();
        return (dailyReturnStats.getMean() * n + getLastDayReturn()) / (n + 1);
    }

    /**
     * Returns an <em>annualized</em> Sharpe Ratio based on daily returns including the “most recent” “in-progress” daily return.
     *
     * <p>The formula used is:
     * <pre>
     *   Sharpe(annual) = sqrt(252) * ( (mean of daily returns - dailyRf) / stdev )
     * </pre>
     * where dailyRf is {@code annualRiskFreeRate/252}.
     *
     * @return the annual Sharpe ratio, or {@code Double.NaN} if insufficient data
     */
    public double getAnnualSharpeRatio() {
        if (dailyReturnStats.getN() < 1) {
            return Double.NaN;
        }
        long n = dailyReturnStats.getN();
        double oldMean = dailyReturnStats.getMean();
        double oldVariance = dailyReturnStats.getVariance();
        double lastDayReturn = getLastDayReturn();
        double newMean = (oldMean * n + lastDayReturn) / (n + 1);
        double newVariance = ((n - 1) * oldVariance + (lastDayReturn - oldMean) * (lastDayReturn - newMean)) / n;
        double newStdev = Math.sqrt(newVariance);

        if (newStdev == 0.0) {
            return Double.NaN;
        }
        double dailyRf = annualRiskFreeRate / 252.0;
        return Math.sqrt(252.0) * ((newMean - dailyRf) / newStdev);
    }

    /**
     * Returns an <em>annualized</em> Sortino Ratio based on daily returns including the “most recent” “in-progress” daily return.
     *
     * <p>The Sortino ratio is computed as:
     * <pre>
     *   Sortino(annual) = sqrt(252) * ( (mean of daily returns - dailyRf) / downsideDeviation )
     * </pre>
     * where dailyRf = (annualRiskFreeRate / 252), and downsideDeviation is the square root of
     * average squared negative deviation from dailyRf.
     * <p>If there have been no negative returns relative to dailyRf, the ratio is set to NaN.
     *
     * @return the annual Sortino ratio, or {@code Double.NaN} if insufficient data
     */
    public double getAnnualSortinoRatio() {
        if (dailyReturnStats.getN() < 1) {
            return Double.NaN;
        }
        long n = dailyReturnStats.getN();
        double meanReturn = dailyReturnStats.getMean();
        double lastDayReturn = getLastDayReturn();
        double newMeanReturn = (meanReturn * n + lastDayReturn) / (n + 1);
        double dailyRf = annualRiskFreeRate / 252.0;
        double diff = dailyRf - lastDayReturn;
        double lastDayDownsideSquared = (diff > 0.0) ? diff * diff : 0.0;
        double downsideDev = Math.sqrt((dailyDownsideSumOfSquares + lastDayDownsideSquared) / (n + 1));
        if (downsideDev == 0.0) {
            return Double.NaN;
        }
        return Math.sqrt(252.0) * ((newMeanReturn - dailyRf) / downsideDev);
    }

    /**
     * Returns the correlation coefficient between time (nanoseconds) and the equity values.
     * Uses standard Pearson correlation from a linear regression fit with intercept.
     *
     * @return correlation coefficient or {@code NaN} if fewer than 2 data points
     */
    public double getTimeEquityCorrelation() {
        return (timeEquityRegression.getN() < 2) ? Double.NaN : timeEquityRegression.getR();
    }

    /**
     * Returns the correlation coefficient between time (nanoseconds) and log(equity).
     * If equity was ever 0.0, that data point is skipped due to undefined logarith.
     *
     * @return correlation coefficient or {@code NaN} if fewer than 2 data points
     */
    public double getTimeLogEquityCorrelation() {
        return (timeLogEquityRegression.getN() < 2) ? Double.NaN : timeLogEquityRegression.getR();
    }

    /**
     * Returns a string representation of this statistics object, suitable for debugging.
     *
     * @return a string containing key metrics like starting equity, highest equity,
     *         max drawdown, sharpe ratio, equity linearity, and so on
     */
    @Override
    public String toString() {
        return String.format(
                "EquitySummaryStatistics{" +
                        "startingEquity=%.2f, totalEquityHigh=%.2f, totalEquityLow=%.2f, " +
                        "endingEquity=%.2f, maxDrawdown=%.2f, maxDrawdownPercent=%.2f, " +
                        "averageDrawdown=%.2f, averageDrawdownPercent=%.2f, dataPoints=%d, " +
                        "annualSharpeRatio=%.4f, annualSortinoRatio=%.4f, " +
                        "timeEquityCorrelation=%.4f}",
                startingEquity,
                totalEquityHigh,
                totalEquityLow,
                endingEquity,
                maxDrawdown,
                maxDrawdownPercent,
                getAverageDrawdown(),
                getAverageDrawdownPercent(),
                dataPoints,
                getAnnualSharpeRatio(),
                getAnnualSortinoRatio(),
                Math.max(getTimeEquityCorrelation(), getTimeLogEquityCorrelation())
        );
    }
}
