package one.chartsy.simulation.reporting;

import one.chartsy.time.Chronological;
import org.openide.util.Lookup;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static java.time.temporal.ChronoUnit.NANOS;
import static java.time.temporal.ChronoUnit.YEARS;

public class EquityInformationSupport {

    public double years(long startTime, long endTime, ZoneId zone) {
        ZonedDateTime start = Chronological.toDateTime(startTime, zone);
        ZonedDateTime end = Chronological.toDateTime(endTime, zone);

        long years = YEARS.between(start, end);
        ZonedDateTime startAnniversaryBefore = start.plusYears(years), startAnniversaryAfter = startAnniversaryBefore.plusYears(1);
        return years + (double)NANOS.between(startAnniversaryBefore, end) / NANOS.between(startAnniversaryBefore, startAnniversaryAfter);
    }

    public double cagr(EquityInformation equity) {
        double years = equity.years();
        return (Math.pow(equity.endingEquity() / equity.startingEquity(), 1/years) - 1.0)*100.0;
    }

    public static EquityInformationSupport instance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final EquityInformationSupport INSTANCE;
        static {
            EquityInformationSupport instance = Lookup.getDefault().lookup(EquityInformationSupport.class);
            INSTANCE = (instance != null)? instance: new EquityInformationSupport();
        }
    }
}
