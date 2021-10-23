package one.chartsy.guava;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class EventBusTest {

    EventBus eventBus = new EventBus();
    static AtomicLong eventCount = new AtomicLong();
    static AtomicLong eventHelper = new AtomicLong();

    public static void main(String[] args) throws IOException {
        new EventBusTest().run();
    }

    public void run() throws IOException {
        eventBus.register(new EventListener());
        ThreadLocalRandom r = ThreadLocalRandom.current();

        System.in.read();
        for (int i = 0; i < 1000; i++)
            eventBus.post((r.nextInt() % 4 == 1)? UUID.randomUUID().toString() : r.nextLong());

        long startTime = System.nanoTime();
        for (int i = 0; i < 200_000_000; i++)
            eventBus.post((r.nextInt() % 4 == 1)? UUID.randomUUID().toString() : (r.nextInt() % 4 == 1)? r.nextLong() : r.nextInt());

        long elapsedTime = System.nanoTime() - startTime;
        System.out.println("TIME INFO: " + eventCount.get() * 1000_000_000L / elapsedTime + " events/sec");
    }

    public static class EventListener {

        private static int eventsHandled;

        @Subscribe
        public void stringEvent(String event) {
            eventHelper.addAndGet(event.length() + event.charAt(0));
            eventCount.incrementAndGet();
        }

        @Subscribe
        public void longEvent(Long event) {
            eventHelper.addAndGet(event);
            eventCount.incrementAndGet();
        }

        @Subscribe
        public void intEvent(Integer event) {
            eventHelper.addAndGet(event);
            eventCount.incrementAndGet();
        }
    }
}
