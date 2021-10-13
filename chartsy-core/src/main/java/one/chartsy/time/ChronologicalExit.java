package one.chartsy.time;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Represents an item that is defined by a point in time and additionally
 * suggests having an exit behavior by an event (e.g. a transaction).
 * 
 * @author Mariusz Bernacki
 *
 */
@FunctionalInterface
public interface ChronologicalExit {
    /**
     * Returns the timestamp of an event or object described by this
     * interface. The result reflects a number of microseconds since the
     * epoch measured with a UTC time-zoned clock.
     * 
     * @return the event closing/exiting time
     */
    long getExitTime();
    
    /**
     * Returns the closing date and time of an event or object described by
     * this interface (e.g. a transaction).
     * 
     * @return the event closing/existing date and time
     */
    default LocalDateTime getExitDate() {
        return Chronological.toDateTime(getExitTime());
    }
    
    /**
     * Returns the closing date and time of an event or object described by this
     * interface in the specified time zone.
     * 
     * @param zone
     *            the time zone used
     * @return the event closing/existing date and time
     */
    default ZonedDateTime getExitDate(ZoneId zone) {
        return Chronological.toDateTime(getExitTime(), zone);
    }
}