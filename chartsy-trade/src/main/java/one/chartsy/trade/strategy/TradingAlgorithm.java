/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import one.chartsy.When;
import one.chartsy.time.Chronological;
import one.chartsy.trade.Execution;
import one.chartsy.trade.MarketUniverseChangeListener;
import one.chartsy.trade.strategy.annotation.LookAheadBiasHazard;
import one.chartsy.trade.data.Position;

import java.time.LocalDate;

public interface TradingAlgorithm extends MarketUniverseChangeListener {

    void onInit(TradingAlgorithmContext runtime);

    void onAfterInit();

    void onExit(ExitState state);

    void onTradingDayStart(LocalDate date);

    void onTradingDayEnd(LocalDate date);

    void doFirst(When when);

    void exitOrders(When when, Position position);

    void entryOrders(When when, Chronological data);

    void doLast(When when);

    @LookAheadBiasHazard
    default void onData(When when, Chronological next, boolean timeTick) { }

    void onExecution(Execution execution);

//    void onCandleClose(When when, Candle c);
//
//    @BacktestLiveIncompatibility(IncompatibilityType.DIFFERENT_BEHAVIOUR)
//    void onCandleOpen(When when, CandleOpen open);
}
