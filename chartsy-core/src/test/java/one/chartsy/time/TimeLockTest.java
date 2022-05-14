/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.time;

import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import one.chartsy.core.ThrowingRunnable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimeLockTest {
	private static final long defaultTimeout = 35; //millis
	private TimeLock timeLock;
	
	@BeforeEach
	void initTimeLockUnderTest_asUnlocked() {
		timeLock = new TimeLock(Duration.ofMillis(defaultTimeout));
		assertFalse(timeLock.isLocked());
	}
	
	@Test
	void lock_locks_immediately_but_unlocks_after_defaultTimeout() throws Exception {
		assertWithinTimeout(() -> {
			timeLock.lock();
			assertTrue(timeLock.isLocked());
		});
		afterTimeout(() -> assertFalse(timeLock.isLocked()));
	}

	@Test
	void relock_locks_immediately_but_unlocks_after_defaultTimeout() throws Exception {
		assertWithinTimeout(() -> {
			timeLock.relock();
			assertTrue(timeLock.isLocked());
		});
		afterTimeout(() -> assertFalse(timeLock.isLocked()));
	}
	
	@Test
	void relock_gives_true_when_not_locked_yet_and_false_otherwise() throws Exception {
		assertWithinTimeout(() -> {
			afterTimeout(() -> assertFalse(timeLock.isLocked()));
			assertTrue(timeLock.relock());
			assertFalse(timeLock.relock());
		});
	}
	
	@Test
	void brieflyCached_caches_immediate_call_results() throws Exception {
		assertWithinTimeout(() -> {
			Callable<Integer> brieflyCachedCall = TimeLock.
					brieflyCached(new AtomicInteger()::incrementAndGet, Duration.ofMillis(defaultTimeout));

			assertEquals(1, (int)brieflyCachedCall.call());
			assertEquals(1, (int)brieflyCachedCall.call());
			assertEquals(1, (int)brieflyCachedCall.call());
		});
	}
	
	@Test
	void brieflyCached_refreshes_call_result_after_timeout() throws Exception {
		Callable<Integer> brieflyCachedCall = TimeLock
				.brieflyCached(new AtomicInteger()::incrementAndGet, Duration.ofMillis(defaultTimeout));

		assertEquals(1, (int)brieflyCachedCall.call());
		afterTimeout(() -> assertEquals(2, (int)brieflyCachedCall.call()));
		afterTimeout(() -> assertEquals(3, (int)brieflyCachedCall.call()));
	}

	@Test
	void brieflyCached_refreshes_call_result_periodically() throws Exception {
		Callable<Integer> brieflyCachedCall = TimeLock
				.brieflyCached(new AtomicInteger()::incrementAndGet, Duration.ofMillis(defaultTimeout));
		
		final long farFarAfterTimeout = currentTimeMillis() + 4*defaultTimeout;
		while (currentTimeMillis() < farFarAfterTimeout) {
			brieflyCachedCall.call();
			sleep(1);
		}
		final int underlyingCallNumber = brieflyCachedCall.call();
		//System.out.println(underlyingCallNumber);
		assertTrue((underlyingCallNumber >= 2 && underlyingCallNumber <= 6),
				"Expected # of underlying calls during 4*timeout time period to be 4 approximately, but got " + underlyingCallNumber + " instead");
	}

	private static void afterTimeout(long millis, ThrowingRunnable<Exception> statement) throws Exception {
		sleep(millis*2);
		statement.run();
	}
	
	private static void afterTimeout(ThrowingRunnable<Exception> statement) throws Exception {
		afterTimeout(defaultTimeout, statement);
	}

	/**
	 * Invalidates failed assertions from execution of the specified
	 * {@code statement} unless the statement executed within the required timeout
	 * {@code millis} or maximum number of retries have been reached.
	 * 
	 * @param millis    the timeout limit which, if exceeded, invalidates failed
	 *                  assertions (max no of retries still applies)
	 * @param statement the statement to execute (possibly multiple times)
	 */
	private static <T extends Exception> void assertWithinTimeout(long millis, ThrowingRunnable<T> statement) throws T {
		final int MAX_ATTEMPTS = 9;
		for (int i = 1; i <= MAX_ATTEMPTS; i++) {
			long startTime = System.nanoTime();
			try {
				statement.run();

			} catch (AssertionError e) {
				long elapsedTime = (System.nanoTime() - startTime)/1000_000;
				if (elapsedTime >= millis && i < MAX_ATTEMPTS)
					continue;
				else throw e;
			}
			break;
		}
	}
	
	private static <T extends Exception> void assertWithinTimeout(ThrowingRunnable<T> statement) throws T {
		assertWithinTimeout(defaultTimeout, statement);
	}
}
