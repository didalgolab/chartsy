package one.chartsy.time;

import java.time.Instant;
import java.time.ZoneId;

public abstract class Clock extends java.time.Clock {

    public abstract long time();

    @Override
    public abstract Clock withZone(ZoneId zone);


    public static Clock systemUTC() {
        return ProxyClock.SYSTEM_UTC;
    }

    public static Clock system(ZoneId zone) {
        return new ProxyClock(java.time.Clock.system(zone));
    }

    public static Clock systemDefaultZone() {
        return new ProxyClock(java.time.Clock.systemDefaultZone());
    }

    private static final class ProxyClock extends Clock {
        private static final ProxyClock SYSTEM_UTC = new ProxyClock(java.time.Clock.systemUTC());
        private final java.time.Clock source;

        ProxyClock(java.time.Clock source) {
            this.source = source;
        }

        @Override
        public long time() {
            Instant in = source.instant();
            return Math.multiplyExact(in.getEpochSecond(), 1000_000) + in.getNano()/1000;
        }

        @Override
        public ZoneId getZone() {
            return source.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new ProxyClock(source.withZone(zone));
        }

        @Override
        public Instant instant() {
            return source.instant();
        }
    }
}
