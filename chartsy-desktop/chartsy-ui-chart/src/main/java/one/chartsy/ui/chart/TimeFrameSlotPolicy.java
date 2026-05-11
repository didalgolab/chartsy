/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.TimeFrame;
import one.chartsy.time.Chronological;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Converts between temporal coordinates and chart slots for regular time frames.
 */
public final class TimeFrameSlotPolicy {
    private enum Kind {
        SECONDS,
        MONTHS
    }

    private final Kind kind;
    private final int amount;
    private final long baseTime;

    private TimeFrameSlotPolicy(Kind kind, int amount, long baseTime) {
        this.kind = kind;
        this.amount = amount;
        this.baseTime = baseTime;
    }

    public static Optional<TimeFrameSlotPolicy> of(TimeFrame timeFrame, long baseTime) {
        if (timeFrame == null)
            return Optional.empty();
        return timeFrame.getAsSeconds()
                .map(seconds -> new TimeFrameSlotPolicy(Kind.SECONDS, seconds.getAmount(), baseTime))
                .or(() -> timeFrame.getAsMonths()
                        .map(months -> new TimeFrameSlotPolicy(Kind.MONTHS, months.getAmount(), baseTime)));
    }

    public long timeAtOffset(int slotOffset) {
        return switch (kind) {
            case SECONDS -> Math.addExact(baseTime, Math.multiplyExact(Math.multiplyExact((long) slotOffset, amount), 1_000_000_000L));
            case MONTHS -> {
                OffsetDateTime base = Chronological.toDateTime(baseTime, Chronological.TIME_ZONE.getRules().getOffset(Chronological.toInstant(baseTime)));
                yield Chronological.toEpochNanos(base.plusMonths(Math.multiplyExact((long) slotOffset, amount)));
            }
        };
    }

    public int slotOffset(long time) {
        return switch (kind) {
            case SECONDS -> {
                long delta = Math.subtractExact(time, baseTime);
                long slotNanos = Math.multiplyExact((long) amount, 1_000_000_000L);
                yield Math.toIntExact(Math.floorDiv(delta, slotNanos));
            }
            case MONTHS -> {
                OffsetDateTime base = Chronological.toDateTime(baseTime, Chronological.TIME_ZONE.getRules().getOffset(Chronological.toInstant(baseTime)));
                OffsetDateTime current = Chronological.toDateTime(time, Chronological.TIME_ZONE.getRules().getOffset(Chronological.toInstant(time)));
                int months = one.chartsy.time.Months.between(base, current).getAmount();
                yield Math.floorDiv(months, amount);
            }
        };
    }
}
