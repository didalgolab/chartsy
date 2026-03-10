/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import one.chartsy.data.structures.DoubleWindowSummaryStatistics;
import one.chartsy.financial.AbstractCandleIndicator;
import one.chartsy.study.ChartStudy;
import one.chartsy.study.LinePlotSpec;
import one.chartsy.study.StudyFactory;
import one.chartsy.study.StudyInputKind;
import one.chartsy.study.StudyKind;
import one.chartsy.study.StudyOutput;
import one.chartsy.study.StudyParameter;
import one.chartsy.study.StudyParameterScope;
import one.chartsy.study.StudyParameterType;
import one.chartsy.study.StudyPlacement;

/**
 * The {@code Quota} indicator measures the <b>liquidity-based maximum recommended capital allocation</b>
 * for an asset, calculated as a fraction of its average daily turnover over a recent trading period.
 *
 * <p>The core principle behind {@code Quota} is to determine a <b>prudent investment threshold</b>,
 * specifically defined as <b>5% of the asset's average daily turnover computed from the latest 60 trading
 * sessions</b>. This provides investors with an <b>actionable metric to gauge maximum safe position size</b>,
 * effectively managing liquidity risk by avoiding excessive capital exposure to assets with insufficient
 * market liquidity.</p>
 *
 * <p>Practically, {@code Quota} serves as a <b>filtering mechanism</b>, identifying instruments with
 * adequate liquidity to comfortably accommodate typical investment sizes. Instruments with {@code Quota}
 * values below certain thresholds (e.g., below <b>1500-3000 PLN</b>) signal potentially
 * hazardous liquidity levels, discouraging significant involvement. Conversely, higher {@code Quota}
 * values indicate assets well-suited for meaningful investment, reinforcing confidence that entering
 * and exiting positions will minimally impact market prices.</p>
 *
 * <p>{@code Quota} complements traditional technical analysis indicators by incorporating <b>essential
 * liquidity context</b>. Used alongside trend, volatility, or momentum indicators, {@code Quota} allows
 * for <b>multidimensional investment assessment</b>, highlighting when an asset's favorable technical
 * outlook aligns with sufficient liquidity conditions. In this role, {@code Quota} aids investors in
 * avoiding the common pitfall of committing substantial funds to illiquid markets, thereby supporting
 * more consistent and sustainable investment performance.</p>
 *
 * <p>Notably, while {@code Quota} excels in <b>liquidity-sensitive markets — such as emerging markets or
 * small-cap equities</b> — it is universally applicable, scaling effectively across instruments ranging
 * from equities to cryptocurrencies. Investors can easily incorporate {@code Quota} into <b>systematic
 * screening processes, risk management routines, or strategic portfolio allocation decisions</b>,
 * enhancing overall investment prudence and market responsiveness.</p>
 *
 * @author Mariusz Bernacki
 *
 */
@ChartStudy(
        name = "Quota",
        label = "Quota ({periods}, {quotaFraction})",
        category = "Market Structure",
        kind = StudyKind.INDICATOR,
        placement = StudyPlacement.OWN_PANEL
)
@StudyParameter(id = "color", name = "Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#000000", order = 100)
@StudyParameter(id = "stroke", name = "Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "THICK_SOLID", order = 110)
@LinePlotSpec(id = "quota", label = "Quota", output = "value", colorParameter = "color", strokeParameter = "stroke", order = 10)
public class Quota extends AbstractCandleIndicator {

    public static final int DEFAULT_PERIODS = 60;
    public static final double DEFAULT_QUOTA_FRACTION = 0.05;

    private final DoubleWindowSummaryStatistics turnoverStats;
    private final double quotaFraction;
    private double quota = Double.NaN;

    @StudyFactory(input = StudyInputKind.CANDLES)
    public static Quota study(
            @StudyParameter(id = "periods", name = "Periods", scope = StudyParameterScope.COMPUTATION, defaultValue = "60", order = 10) int periods,
            @StudyParameter(id = "quotaFraction", name = "Quota Fraction", scope = StudyParameterScope.COMPUTATION, defaultValue = "0.05", order = 20) double quotaFraction
    ) {
        return new Quota(periods, quotaFraction);
    }

    public Quota() {
        this(DEFAULT_PERIODS, DEFAULT_QUOTA_FRACTION);
    }

    public Quota(int periods, double quotaFraction) {
        if (periods <= 1)
            throw new IllegalArgumentException("Periods must be greater than 1");
        if (quotaFraction <= 0.0 || quotaFraction > 1.0)
            throw new IllegalArgumentException("Quota fraction must be between 0 and 1");

        this.turnoverStats = new DoubleWindowSummaryStatistics(periods);
        this.quotaFraction = quotaFraction;
    }

    @Override
    public void accept(Candle candle) {
        turnoverStats.accept(candle.volume() * candle.close());

        if (turnoverStats.isFull()) {
            double adjustedSum = turnoverStats.getSum() - turnoverStats.getMax();
            quota = quotaFraction * adjustedSum / (turnoverStats.getCount() - 1);
        }
    }

    @Override
    public boolean isReady() {
        return turnoverStats.isFull();
    }

    @Override
    @StudyOutput(id = "value", name = "Quota", order = 10)
    public double getLast() {
        return quota;
    }
}
