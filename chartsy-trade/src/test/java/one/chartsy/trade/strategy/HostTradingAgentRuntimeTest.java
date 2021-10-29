package one.chartsy.trade.strategy;

import one.chartsy.scheduling.EventScheduler;
import one.chartsy.time.Chronological;
import one.chartsy.time.Clock;
import org.junit.jupiter.api.Test;
import org.openide.util.Lookup;
import org.opentest4j.AssertionFailedError;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ServiceLoader;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class HostTradingAgentRuntimeTest {

    final TradingAgentRuntime local = new HostTradingAgentRuntime();

    @Test
    void is_installed_as_a_service() {
        var service = Lookup.getDefault().lookup(TradingAgentRuntime.class);
        assertInstanceOf(HostTradingAgentRuntime.class, service);

        service = ServiceLoader.load(TradingAgentRuntime.class).findFirst().orElseThrow(AssertionFailedError::new);
        assertInstanceOf(HostTradingAgentRuntime.class, service);
    }

    @Test
    void provides_system_Clock() {
        var clock = local.clock();

        //require same zone
        assertEquals(clock.getZone(), ZoneId.systemDefault());

        //require same instant
        Instant instantBefore = Instant.now();
        assertThat(clock.instant())
                .isBetween(instantBefore, Instant.now());

        //require same time
        long timeBefore = Chronological.now();
        assertThat(clock.time())
                .isBetween(timeBefore, Chronological.now());
    }

    @Test
    void withClock_gives_another_Clock_to_use() {
        var anotherClock = Clock.system(ZoneId.of("America/Los_Angeles"));
        assertEquals(anotherClock, local.withClock(anotherClock).clock());
    }

    @Test
    void withClockAtZone_replaces_Clock_and_zone() {
        var anotherZone = ZoneId.of("America/Los_Angeles");
        assertEquals(anotherZone, local.withClockAtZone(anotherZone).clock().getZone());
    }

    @Test
    void provides_Scheduler() throws InterruptedException {
        EventScheduler scheduler = local.scheduler();

        Semaphore sync = new Semaphore(0);
        scheduler.schedule((Chronological.now() + 1000L), sync::release);
        assertTrue(sync.tryAcquire(20, TimeUnit.MILLISECONDS), "Scheduled event triggered");
    }
}