/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.vector;

import one.chartsy.collections.DoubleArray;
import one.chartsy.data.DimensionMismatchException;
import one.chartsy.data.RealVector;

import java.util.Arrays;
import java.util.function.DoubleBinaryOperator;

/**
 * A compact, efficient implementation of {@code RealVector} using a
 * double array as the underlying data structure. This class provides
 * a memory-efficient representation of dense real-valued vectors and
 * implements the operations defined in the {@code RealVector} interface.
 */
public class PackedRealVector extends AbstractRealVector {

    private final double[] values;

    private static PackedRealVector create(double[] values) {
        return new PackedRealVector(values);
    }

    public static PackedRealVector from(double[] values) {
        return fromRange(values, 0, values.length);
    }

    public static PackedRealVector fromRange(double[] values, int from, int to) {
        return create(Arrays.copyOfRange(values, from, to));
    }

    private PackedRealVector(double[] values) {
        this.values = values;
    }

    @Override
    public final int size() {
        return values.length;
    }

    @Override
    public final double get(int index) {
        return values[index];
    }

    @Override
    public final double[] values() {
        return values.clone();
    }

    @Override
    public RealVector scale(double scalar) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i] * scalar;
        }
        return create(result);
    }

    @Override
    public double norm() {
        double sum = 0;
        for (int i = 0; i < size(); i++) {
            double v = get(i);
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    @Override
    public RealVector normalize() {
        double norm = norm();
        if (norm == 0) {
            throw new ArithmeticException("Cannot normalize a zero-length vector.");
        }
        return scale(1 / norm);
    }

    @Override
    public RealVector add(RealVector other) {
        checkDimension(other);
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i] + other.get(i);
        }
        return create(result);
    }

    @Override
    public double dot(RealVector other) {
        checkDimension(other);
        double result = 0.0;
        for (int i = 0; i < values.length; i++) {
            result += values[i] * other.get(i);
        }
        return result;
    }

    @Override
    public RealVector fold(DoubleBinaryOperator function, Iterable<RealVector> others) {
        double[] values = values();
        others.forEach(vect -> {
            checkDimension(vect);
            for (int i = 0; i < values.length; i++)
                values[i] = function.applyAsDouble(values[i], vect.get(i));
        });

        return from(values);
    }

    @Override
    public RealVector join(RealVector... others) {
        if (others.length == 0)
            return this;

        Builder builder = toBuilder();
        for (RealVector other : others)
            builder.addAll(other.values());

        return builder.build();
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PackedRealVector vec && Arrays.equals(values, vec.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    /**
     * Checks if the dimension of the given vector matches the dimension of this vector.
     *
     * @param other the vector to compare dimensions with
     * @throws DimensionMismatchException if the dimensions do not match
     */
    protected void checkDimension(RealVector other) throws DimensionMismatchException {
        if (other.size() != this.size()) {
            throw new DimensionMismatchException("Vector dimensions do not match: "
                    + this.size() + " and " + other.size());
        }
    }

    @Override
    public Builder toBuilder() {
        return new Builder(values.length).addAll(values);
    }

    public static class Builder implements RealVector.Builder {
        private final DoubleArray values;

        public Builder() {
            values = new DoubleArray();
        }

        public Builder(int capacity) {
            values = new DoubleArray(capacity);
        }

        @Override
        public Builder add(double value) {
            values.add(value);
            return this;
        }

        @Override
        public Builder addAll(double... values) {
            this.values.addAll(values);
            return this;
        }

        @Override
        public Builder setValueAt(int index, double value) {
            values.set(index, value);
            return this;
        }

        @Override
        public PackedRealVector build() {
            return new PackedRealVector(values.toArray());
        }
    }
}
