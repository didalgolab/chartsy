package one.chartsy.simulation.engine;

import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.messaging.MarketMessageSource;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.data.BestBidOfferEvent;

import java.util.Comparator;
import java.util.PriorityQueue;

/** Stable event-time merge: completions, quotes, then openings, preserving each source's record order. */
final class MarketReplayMerge implements AutoCloseable {
    private record Head(MarketEvent event, int sourceOrdinal, MarketMessageSource source) { }
    private final PriorityQueue<Head> queue = new PriorityQueue<>(Comparator
            .comparingLong((Head h) -> h.event.time())
            .thenComparingInt(h -> phase(h.event))
            .thenComparingInt(Head::sourceOrdinal));

    private static int phase(MarketEvent event) {
        return switch (event) {
            case BarOpen _ -> 2;
            case BestBidOfferEvent _ -> 1;
            default -> 0;
        };
    }

    void add(MarketMessageSource source, int ordinal) {
        try {
            var event = source.getMessage();
            if (event == null)
                source.close();
            else
                queue.add(new Head(event, ordinal, source));
        } catch (RuntimeException | Error failure) {
            source.close();
            throw failure;
        }
    }

    int poll(MarketMessageHandler handler, int limit) {
        if (limit <= 0 || queue.isEmpty())
            return 0;
        long time = queue.peek().event.time();
        int count = 0;
        while (count < limit && !queue.isEmpty() && queue.peek().event.time() == time) {
            var head = queue.remove();
            try {
                handler.onMarketMessage(head.event);
            } catch (RuntimeException | Error failure) {
                head.source.close();
                throw failure;
            }
            count++;
            add(head.source, head.sourceOrdinal);
        }
        return count;
    }

    @Override public void close() {
        queue.forEach(head -> head.source.close());
        queue.clear();
    }
}
