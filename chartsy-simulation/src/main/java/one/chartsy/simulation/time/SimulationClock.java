/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation.time;

import one.chartsy.time.Chronological;
import one.chartsy.time.Clock;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class SimulationClock extends Clock {

    private final ZoneId zone;

    private volatile long time;

    public SimulationClock(ZoneId zone, long time) {
        this.zone = zone;
        this.time = time;
    }

    @Override
    public long time() {
        return time;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public SimulationClock withZone(ZoneId zone) {
        return new SimulationClock(zone, time);
    }

    @Override
    public Instant instant() {
        return Chronological.toInstant(time);
    }

    public final void setTime(Chronological data) {
        this.time = data.getTime();
    }

    public final void setTime(LocalDateTime datetime) {
        this.time = Chronological.toEpochMicros(datetime);
    }

    public final void setTime(long time) {
        this.time = time;
    }
}
