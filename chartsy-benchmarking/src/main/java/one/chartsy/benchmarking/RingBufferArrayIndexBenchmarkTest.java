/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.benchmarking;

import java.util.concurrent.ThreadLocalRandom;

public class RingBufferArrayIndexBenchmarkTest {

	private static final long mask = (1 << 19) - 1;
	private static final int mask2 = (1 << 19) - 1;

	public static void main(String[] args) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (long i = 0; i < 1000_000_000; i++) {
			long nextWrite = r.nextLong();
			int v1 = indexOf1(nextWrite, 0);
			int v2 = indexOf2(nextWrite, 0);
			if (v1 != v2)
				System.out.println("DIFF: "+v1+" != "+v2);
		}
	}

	private static int indexOf1(long nextWrite, int offset) {
		return (int) ((nextWrite - offset - 1) & mask);
	}

	private static int indexOf2(long nextWrite, int offset) {
		return ((int)nextWrite - offset - 1) & mask2;
	}
}
