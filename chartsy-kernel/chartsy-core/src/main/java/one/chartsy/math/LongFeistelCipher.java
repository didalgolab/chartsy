package one.chartsy.math;

/**
 * Provides a reversible permutation of a {@code Long}.
 *
 */
public class LongFeistelCipher {

    /** Private randomly generated secret key used to describe the permutation. */
    private final static long SECRET_KEY = 0x2df474fa3dd35429L;
    private final static long LOW_HALF_MASK = 0xFFFFFFFFL;
    private final static int HALF_SHIFT = 32;
    private final static int NUM_ROUNDS = 4;

    private long secretKey;
    private final long[] roundKeys = new long[NUM_ROUNDS];

    public LongFeistelCipher() { this(SECRET_KEY); }

    public LongFeistelCipher(long key) {
        setKey(key);
    }

    private void setKey(long newKey) {
        secretKey = newKey;

        roundKeys[0] = secretKey & LOW_HALF_MASK;
        roundKeys[1] = ~(secretKey & LOW_HALF_MASK);
        roundKeys[2] = secretKey >>> HALF_SHIFT;
        roundKeys[3] = ~(secretKey >>> HALF_SHIFT);
    }

    /** Returns the current value of the key. */
    public long getKey() { return secretKey; }

    public long encode(long plain) {
        long rhs = plain & LOW_HALF_MASK;
        long lhs = plain >>> HALF_SHIFT;

        for (int i = 0; i < NUM_ROUNDS; i++) {
            if (i > 0) {
                long tmp = lhs;
                lhs = rhs;
                rhs = tmp;
            }
            rhs ^= F(lhs, i);
        }

        return (lhs << HALF_SHIFT) + (rhs & LOW_HALF_MASK);
    }

    public long decode(long cypher) {
        long rhs = cypher & LOW_HALF_MASK;
        long lhs = cypher >>> HALF_SHIFT;

        for (int i = 0; i < NUM_ROUNDS; ++i) {
            if (i > 0) {
                long tmp = lhs;
                lhs = rhs;
                rhs = tmp;
            }
            rhs ^= F(lhs, NUM_ROUNDS - 1 - i);
        }

        return (lhs << HALF_SHIFT) + (rhs & LOW_HALF_MASK);
    }

    private long F(long num, int round) {
        num ^= roundKeys[round];
        num *= num;
        return (num >>> HALF_SHIFT) ^ (num & LOW_HALF_MASK);
    }
}
