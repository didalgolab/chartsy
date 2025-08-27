/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.math;

/**
 * {@code EqualTolerantMath} centralizes double comparisons under a symmetric absolute tolerance.
 * Differences up to the tolerance collapse to equality, and values within
 * [-tolerance, +tolerance] collapse to zero. NaN is treated as unordered
 * (comparison predicates return false; only isNotEqual/isNotZero return true),
 * while infinities compare exactly. The tolerance must be finite and non-negative.
 *
 * @author Mariusz Bernacki
 */
public final class EqualTolerantMath {

    private final double tolerance;

    /**
     * Creates a comparator that applies the given absolute tolerance to all checks.
     *
     * @param tolerance non-negative, finite symmetric tolerance; acts like a deadband around zero
     * @throws IllegalArgumentException if tolerance is negative, infinite, or NaN
     */
    public EqualTolerantMath(double tolerance) {
        if (Double.isNaN(tolerance) || Double.isInfinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and >= 0.0");
        }
        this.tolerance = tolerance;
    }

    /**
     * Gives {@code true} when the magnitude is within the deadband around zero.
     *
     * @param x value to test against zero with tolerance
     * @return true if |x| <= tolerance (NaN and infinities yield false)
     */
    public boolean isZero(double x) {
        return Math.abs(x) <= tolerance;
    }

    /**
     * Logical complement of isZero.
     *
     * @param x value to test for being outside the zero deadband
     * @return true if not isZero(x); NaN is flagged as not-zero
     */
    public boolean isNotZero(double x) {
        return !isZero(x);
    }

    /**
     * Strictly positive beyond the deadband; tiny jitter does not count.
     *
     * @param x candidate value
     * @return true if x > tolerance (NaN yields false)
     */
    public boolean isPositive(double x) {
        return x > tolerance;
    }

    /**
     * Strictly negative beyond the deadband.
     *
     * @param x candidate value
     * @return true if x < -tolerance (NaN yields false)
     */
    public boolean isNegative(double x) {
        return x < -tolerance;
    }

    /**
     * Approximate equality: exact match (covers infinities) or small absolute difference.
     *
     * @param a left operand
     * @param b right operand
     * @return true if a == b, or |a - b| <= tolerance; NaN values are never equal
     */
    public boolean isEqual(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }
        // (a == b) covers exact equality, +0.0/-0.0, and equal infinities
        return (a == b) || Math.abs(a - b) <= tolerance;
    }

    /**
     * Negation of isEqual; useful to guard against NaN quickly.
     *
     * @param a left operand
     * @param b right operand
     * @return true if not isEqual(a, b)
     */
    public boolean isNotEqual(double a, double b) {
        return !isEqual(a, b);
    }

    /**
     * Strict ordering with tolerance: requires a gap larger than the deadband (think a + tolerance < b).
     *
     * @param a candidate smaller value
     * @param b candidate larger value
     * @return true if a < b and not isEqual(a, b); false if either is NaN
     */
    public boolean isLessThan(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }
        return a < b && !isEqual(a, b);
    }

    /**
     * Non-strict ordering under tolerance.
     *
     * @param a candidate smaller-or-equal value
     * @param b candidate larger-or-equal value
     * @return true if a < b or isEqual(a, b); false if either is NaN
     */
    public boolean isLessThanOrEqual(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }
        return a < b || isEqual(a, b);
    }

    /**
     * Mirror of isLessThan with swapped operands.
     *
     * @param a candidate larger value
     * @param b candidate smaller value
     * @return true if b isLessThan a
     */
    public boolean isGreaterThan(double a, double b) {
        return isLessThan(b, a);
    }

    /**
     * True when not less-than under tolerance; equality within the deadband counts as greater-or-equal.
     *
     * @param a candidate larger-or-equal value
     * @param b candidate smaller-or-equal value
     * @return true if neither operand is NaN and not isLessThan(a, b); false if either is NaN
     */
    public boolean isGreaterThanOrEqual(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }
        return !isLessThan(a, b);
    }
}
