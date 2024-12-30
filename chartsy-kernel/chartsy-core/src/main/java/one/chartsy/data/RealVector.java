/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data;

import one.chartsy.data.vector.PackedRealVector;

import java.util.function.DoubleBinaryOperator;

/**
 * Represents an abstraction for a vector with double-precision elements.
 * This interface provides a foundational set of operations for working
 * with real-valued vectors, enabling operations such as scaling,
 * normalization, and element-wise manipulation.
 *
 * <p>Implementations of this interface are expected to provide efficient
 * storage and manipulation of vector elements. This interface facilitates
 * a range of mathematical and geometric operations commonly used in
 * high-performance computing, data analysis, and machine learning.</p>
 *
 * @author Mariusz Bernacki
 */
public interface RealVector {

    /**
     * Returns the dimension of the vector.
     *
     * @return the number of elements in this vector
     */
    int size();

    /**
     * Retrieves the element at the specified index.
     *
     * @param index the index of the element to retrieve
     * @return the value of the element at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    double get(int index);

    /**
     * Creates a vector from an array of doubles.
     * This factory method ensures isolation from changes to the input array.
     *
     * @param values the array of values
     * @return a new {@code RealVector} with the given values.
     */
    static RealVector from(double[] values) {
        return PackedRealVector.from(values);
    }

    /**
     * Creates a vector from a range of elements in an array of doubles.
     * This factory method ensures isolation from changes to the input array.
     *
     * @param values the array of values
     * @param from the starting index of the range to be copied, inclusive
     * @param to the ending index of the range to be copied, exclusive
     * @return a new {@code RealVector} with the values from the specified range.
     * @throws ArrayIndexOutOfBoundsException if {@code from < 0}, {@code to > values.length}
     * @throws IllegalArgumentException if {@code from > to}
     */
    static RealVector fromRange(double[] values, int from, int to) {
        return PackedRealVector.fromRange(values, from, to);
    }

    /**
     * Creates a vector from a list of values.
     *
     * @param values the list of values
     * @return a new {@code RealVector} with the given values.
     */
    static RealVector fromValues(double... values) {
        return PackedRealVector.from(values);
    }

    /**
     * Retrieves a copy of the vector's elements as an array of doubles.
     * The returned array is a caller-private copy, meaning that changes
     * to the returned array do not affect the contents of the vector.
     *
     * @return a new array containing a copy of the vector's elements
     */
    double[] values();

    /**
     * Performs a scaling operation on this vector by a scalar multiplier.
     *
     * @param scalar the scalar value by which this vector is to be scaled
     * @return a new {@code RealVector} instance representing the result of the scaling
     */
    RealVector scale(double scalar);

    /**
     * Computes the Euclidean norm (L2 norm) of this vector.
     * The norm is defined as the square root of the sum of the squares of the elements.
     *
     * @return the Euclidean norm
     * @throws ArithmeticException if any element value overflows during computation
     */
    double norm();

    /**
     * Normalizes this vector, returning a unit vector pointing in the same direction.
     *
     * @return a new {@code RealVector} instance representing the normalized vector
     * @throws ArithmeticException if the vector is zero-length
     */
    RealVector normalize();

    /**
     * Adds another vector to this vector.
     *
     * @param other the vector to be added to this vector
     * @return a new {@code RealVector} instance representing the result of the vector addition
     * @throws IllegalArgumentException if the other vector is not of the same dimension
     */
    RealVector add(RealVector other);

    /**
     * Gives the scalar product of this and the {@code other} vector.
     *
     * @param other the vector with which to calculate the dot product
     * @return the scalar result of the dot product
     * @throws IllegalArgumentException if the other vector is not of the same dimension
     */
    double dot(RealVector other);

    /**
     * Gives the sum of all elements in this vector.
     *
     * @return the sum of the vector elements, or {@code 0} if the vector is empty
     */
    double sum();

    /**
     * Gives the minimum element in this vector.
     *
     * @return the minimum vector element, or {@code Double.POSITIVE_INFINITY} if the vector is empty
     */
    double min();

    /**
     * Gives the maximum element in this vector.
     *
     * @return the maximum vector element, or {@code Double.NEGATIVE_INFINITY} if the vector is empty
     */
    double max();

    /**
     * Gives the average of all elements in this vector.
     *
     * @return the average of the vector elements, or {@code 0} if the vector is empty
     */
    double mean();

    /**
     * Performs a <i>reduction</i> on the components of this vector, using the provided
     * identity value and an associative accumulation function, returning the reduced value.
     * This is equivalent to:
     * <pre>{@code
     *     T result = identity;
     *     for (int i = 0; i < size(); i++)
     *         result = function.applyAsDouble(result, get(i));
     *     return result;
     * }</pre>
     *
     * <p>The identity value should be an identity for the binary operator; this means that
     * applying the operator between the identity value and any element of the vector should
     * return that element.</p>
     *
     * <p>For example, if the vector contains the elements [a, b, c], the binary operator function
     * represents addition, and the identity value is 0, the result would be a sum of all elements:
     * ((((0 + a) + b) + c).</p>
     *
     * <p>The binary operator should be associative to guarantee deterministic results. Providing
     * an identity value ensures that the method can handle empty vectors by returning the identity
     * value in such cases.</p>
     *
     * @param identity the identity value for the binary operator
     * @param function the binary operator to apply to the elements
     * @return the result of applying the binary operator cumulatively to the elements of the vector,
     *         starting with the identity value
     */
    double reduce(double identity, DoubleBinaryOperator function);

    /**
     * Performs an element-wise folding operation on this vector with a collection of other vectors.
     * This method applies a specified binary operator to corresponding elements of this vector
     * and each vector in the provided iterable collection. The result of the binary operator
     * is then stored in the corresponding element of a new result vector.
     *
     * <p>The fold operation effectively combines each element of this vector (the initial result)
     * with the corresponding elements from each vector in the collection, using the provided
     * binary operator function. The operation produces a new vector where each element is the
     * result of the cumulative fold operation.</p>
     *
     * @param function the operator to apply element-wise
     * @param others a sequence of the other vectors to be folded with this vector
     * @return a newly created vector representing the result of the element-wise folding
     * @throws IllegalArgumentException if any vector in the others is not of the same dimension
     *                                  as this vector
     */
    RealVector fold(DoubleBinaryOperator function, Iterable<RealVector> others);

    /**
     * Concatenates this vector with one or more other vectors.
     *
     * @param others the vectors to be concatenated with this vector
     * @return a new {@code RealVector} instance representing the concatenated vector
     */
    RealVector join(RealVector... others);

    /**
     * Creates a new Builder instance pre-populated with the elements of this vector.
     *
     * @return a new Builder instance
     */
    Builder toBuilder();

    /**
     * Allows incremental construction of a {@code RealVector}.
     */
    interface Builder {

        /**
         * Adds a value to the vector.
         *
         * @param value to add
         * @return this builder
         */
        Builder add(double value);

        /**
         * Adds multiple values to the vector.
         *
         * @param values to add
         * @return this builder
         */
        Builder addAll(double... values);

        /**
         * Sets the value at the specified index in the vector being built.
         *
         * @param index the index at which to set the value
         * @param value the value to set at the specified index
         * @return this builder
         * @throws IndexOutOfBoundsException if the index is out of range
         *         (index &lt; 0 || index &gt;= current size of the vector being built)
         */
        Builder setValueAt(int index, double value);

        /**
         * Creates the {@code RealVector} from added elements.
         *
         * @return the {@code RealVector}
         */
        RealVector build();
    }
}
