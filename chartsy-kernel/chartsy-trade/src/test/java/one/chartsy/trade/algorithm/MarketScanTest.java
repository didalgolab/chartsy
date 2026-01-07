/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.data.SimpleCandle;
import one.chartsy.data.stream.Message;
import one.chartsy.data.stream.QueuedMessageBuffer;
import one.chartsy.data.structures.DoubleWindowSummaryStatistics;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.time.Chronological;
import one.chartsy.time.Clock;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MarketScanTest {

    @Test
    void marketScan_returns_top_turnover_symbols() {
        int window = 5;
        int bars = window;
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        SymbolIdentity aaa = SymbolIdentity.of("AAA");
        SymbolIdentity bbb = SymbolIdentity.of("BBB");

        var scan = MarketScan.<DoubleWindowSummaryStatistics, List<SymbolIdentity>>builder(
                        symbol -> new DoubleWindowSummaryStatistics(window),
                        states -> TurnoverSnapshot.topSymbols(states, window, 2)
                )
                .onBar((symbol, state, candle) -> state.add(candle.volume() * candle.close()))
                .build();

        DefaultAlgorithmContext context = new DefaultAlgorithmContext(
                "MarketScanTest",
                Clock.systemUTC(),
                new QueuedMessageBuffer<Message>(128)
        );
        Algorithm algorithm = scan.algorithmFactory().create(context);

        algorithm.open();
        for (int i = 0; i < bars; i++) {
            LocalDateTime time = startDate.plusDays(i).atTime(23, 59, 59);
            long epochNanos = Chronological.toEpochNanos(time);
            algorithm.onMarketMessage(new TradeBar.Of(aaa, candle(epochNanos, 10, 2000)));
            algorithm.onMarketMessage(new TradeBar.Of(bbb, candle(epochNanos, 10, 1000)));
        }
        algorithm.close();

        List<SymbolIdentity> top = scan.resultOrDefault(List.of());
        assertThat(top).hasSize(2);
        assertThat(top.getFirst().name()).isEqualTo("AAA");
        assertThat(top.get(1).name()).isEqualTo("BBB");
    }

    private static Candle candle(long time, double price, double volume) {
        return SimpleCandle.of(time, price, price, price, price, volume);
    }

    private record TurnoverSnapshot(SymbolIdentity symbol, double avgTurnover, LocalDate lastDate) {
        static List<SymbolIdentity> topSymbols(
                Map<SymbolIdentity, MarketScan.ScanSnapshot<DoubleWindowSummaryStatistics>> states,
                int window,
                int limit) {
            List<TurnoverSnapshot> eligible = states.values().stream()
                    .map(snapshot -> TurnoverSnapshot.from(snapshot, window))
                    .filter(snapshot -> snapshot != null)
                    .toList();
            if (eligible.isEmpty())
                return List.of();

            LocalDate rankingDate = eligible.stream()
                    .map(TurnoverSnapshot::lastDate)
                    .max(LocalDate::compareTo)
                    .orElse(LocalDate.MIN);

            return eligible.stream()
                    .filter(snapshot -> snapshot.lastDate().equals(rankingDate))
                    .sorted((left, right) -> Double.compare(right.avgTurnover(), left.avgTurnover()))
                    .limit(limit)
                    .map(TurnoverSnapshot::symbol)
                    .toList();
        }

        private static TurnoverSnapshot from(MarketScan.ScanSnapshot<DoubleWindowSummaryStatistics> snapshot, int window) {
            if (snapshot == null || snapshot.lastBar() == null || snapshot.state().getCount() < window)
                return null;
            double avg = snapshot.state().getAverage();
            if (!Double.isFinite(avg))
                return null;
            return new TurnoverSnapshot(snapshot.symbol(), avg, snapshot.lastBar().getDate());
        }
    }
}
