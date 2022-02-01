package one.chartsy.time;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An object which can be locked and gets automatically unlocked when the
 * specified or default time duration passes. An example usage of the
 * {@code TimeLock} can be limiting (throttling) access frequency to scarce
 * resources (e.g. database queries).
 * 
 * <pre>
 *     Callable&lt;Boolean&gt; checkStatus = <b>TimeLock.brieflyCached</b>(this::askUnderlying, Duration.ofSeconds(1));
 *     for (int i = 0; i < 100_000_000; i++)
 *         if (checkStatus.call())
 *             ...
 * </pre>
 * <p>
 * This class is thread-safe.
 * 
 * @author Mariusz Bernacki
 *
 */
public class TimeLock {
	/** A default duration used by unparameterized {@link #lock()} and {@link #relock()} operations. */
	private final Duration defaultDuration;
	/** The point in time after which this {@code TimeLock} is automatically unlocked. */
	private volatile long lockedUntil = Long.MIN_VALUE;

	
	public TimeLock(Duration defaultDuration) {
		this.defaultDuration = defaultDuration;
	}
	
	public void lock() {
		lock(defaultDuration);
	}
	
	public void lock(Duration duration) {
		lock(System.nanoTime(), duration);
	}
	
	private void lock(long timestamp, Duration duration) {
		lockedUntil = timestamp + duration.toNanos();
	}
	
	public boolean relock() {
		return relock(defaultDuration);
	}
	
	public boolean relock(Duration duration) {
		long timestamp = System.nanoTime();
		boolean relock = !isLocked(timestamp);
		if (relock)
			lock(timestamp, duration);
		return relock;
	}
	
	public boolean isLocked() {
		return isLocked(System.nanoTime());
	}
	
	private boolean isLocked(long when) {
		return (when < lockedUntil);
	}

	/**
	 * Returns a new {@code Callable} that caches the result of the provided
	 * {@code Callable}'s call for the specified amount of time. The returned
	 * {@code Callable} is thread-safe if and only if the specified
	 * {@code operation} is thread-safe.
	 * 
	 * @param operation the operation whose result needs to be cached
	 * @param duration  the caching time duration
	 * @return the new Callable instance
	 */
	public static <V> Callable<V> brieflyCached(Callable<V> operation, Duration duration) {
		final var timeLock = new TimeLock(duration);
		final var cachedResult = new AtomicReference<V>();
		return () -> {
			if (timeLock.relock())
				cachedResult.set(operation.call());
			return cachedResult.get();
		};
	}
}
