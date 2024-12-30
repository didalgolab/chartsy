package one.chartsy.util;

import java.util.concurrent.ThreadLocalRandom;

public class Test {

	public static void main(String[] args) {
		ThreadLocalRandom r = ThreadLocalRandom.current();

		int rr = 0, cnt = 0;
		for (int i = 0; i < Integer.MAX_VALUE; i++) {
			rr += r.nextInt();
			cnt++;
			if ((i & (1 << 16)) == 0)
				System.out.println("cnt: " + cnt + "; r="+rr);
		}
	}
}
