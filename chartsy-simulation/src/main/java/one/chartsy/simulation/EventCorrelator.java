/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation;

import one.chartsy.data.structures.PriorityMap;
import one.chartsy.data.TimedEntry;
import one.chartsy.scheduling.EventScheduler;
import one.chartsy.simulation.time.SimulationClock;
import one.chartsy.time.Chronological;

class EventCorrelator implements EventScheduler {

    private final PriorityMap<Chronological, EventHandler> timedEvents = new PriorityMap<>();

    public void clear() {
        timedEvents.clear();
    }

    public void addTimedEvent(Chronological event, EventHandler handler) {
        timedEvents.put(event, handler);
    }

    public void triggerEventsUpTo(long time, SimulationClock simClock) {
        Chronological event;
        while (!timedEvents.isEmpty() && (event = timedEvents.peekKey()).getTime() <= time) {
            simClock.setTime(event);
            timedEvents.remove().handle(event);
        }
        simClock.setTime(time);
    }

    @Override
    public void schedule(long triggerTime, Runnable event) {
        addTimedEvent(new TimedEntry<Void>(triggerTime, null), new RunnableHandler(event));
    }

    @FunctionalInterface
    interface EventHandler {
        void handle(Chronological event);
    }

    static record RunnableHandler(Runnable action) implements EventHandler {
        @Override
        public void handle(Chronological event) {
            action.run();
        }
    }
}
