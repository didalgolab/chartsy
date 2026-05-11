package one.chartsy.charting.util.java2d;

/// Polynomial root solvers used by [ShapeUtil] when measuring distances to curved path segments.
///
/// Quadratic and cubic Bezier distance tests reduce to finding roots of derivative polynomials in
/// the segment parameter range. This utility provides direct real-root solvers for degrees `1..3`
/// and falls back to a Sturm-sequence based isolator for higher-degree cases.
///
/// Every solver writes real solutions into a caller-supplied buffer and returns the number of
/// entries written. A return value of `-1` means the polynomial degenerates to the zero
/// polynomial, so every real number is a solution.
final class PathSegmentDistanceAlgorithms {

    private PathSegmentDistanceAlgorithms() {
    }

    /// Evaluates `coefficients` at `x` with Horner's method.
    ///
    /// Coefficients are ordered from constant term upward.
    private static double evaluatePolynomial(double[] coefficients, double x) {
        int degree = coefficients.length - 1;
        double value = coefficients[degree];

        for (int coefficientIndex = degree - 1; coefficientIndex >= 0; --coefficientIndex)
            value = value * x + coefficients[coefficientIndex];

        return value;
    }

    /// Divides one polynomial by another and returns only the quotient coefficients.
    ///
    /// This is used when repeated factors collapse the Sturm sequence and the solver needs to
    /// continue with the reduced polynomial.
    private static double[] dividePolynomial(double[] dividendCoefficients, double[] divisorCoefficients) {
        int dividendDegree = dividendCoefficients.length - 1;
        int divisorDegree = divisorCoefficients.length - 1;
        double[] remainderCoefficients = new double[dividendDegree + 1];
        System.arraycopy(dividendCoefficients, 0, remainderCoefficients, 0, dividendDegree + 1);

        double[] quotientCoefficients = new double[dividendDegree - divisorDegree + 1];
        for (; dividendDegree >= divisorDegree; --dividendDegree) {
            double quotientCoefficient = remainderCoefficients[dividendDegree] / divisorCoefficients[divisorDegree];
            quotientCoefficients[dividendDegree - divisorDegree] = quotientCoefficient;

            for (int divisorIndex = 0; divisorIndex < divisorDegree; ++divisorIndex)
                remainderCoefficients[divisorIndex + dividendDegree - divisorDegree] -=
                        quotientCoefficient * divisorCoefficients[divisorIndex];
        }

        return quotientCoefficients;
    }

    /// Counts sign changes in a Sturm sequence at `x`.
    ///
    /// The negative encoding `-1 - changes` signals that `x` itself is an exact root of the
    /// leading polynomial.
    private static int countSturmSignChanges(double[][] sturmSequence, int sequenceLength, double x) {
        boolean previousNegative = sturmSequence[sequenceLength - 1][0] < 0.0D;
        int signChanges = 0;

        for (int polynomialIndex = sequenceLength - 2; polynomialIndex >= 1; --polynomialIndex) {
            double value = evaluatePolynomial(sturmSequence[polynomialIndex], x);
            if (value != 0.0D) {
                boolean negative = value < 0.0D;
                if (negative != previousNegative)
                    ++signChanges;

                previousNegative = negative;
            }
        }

        double leadingValue = evaluatePolynomial(sturmSequence[0], x);
        if (leadingValue != 0.0D) {
            boolean negative = leadingValue < 0.0D;
            if (negative != previousNegative)
                ++signChanges;

            return signChanges;
        }
        return -1 - signChanges;
    }

    /// Recursively isolates roots inside `[min, max]` using precomputed sign-change counts.
    private static int isolateRoots(double[][] sturmSequence, int sequenceLength, double min,
                                    int minSignChanges, double max, int maxSignChanges, int solutionIndex, double[] solutions) {
        if (minSignChanges > maxSignChanges) {
            double midpoint = (min + max) * 0.5D;
            if (min < midpoint && midpoint < max) {
                int midpointSignChanges = countSturmSignChanges(sturmSequence, sequenceLength, midpoint);
                int adjustedMidpointSignChanges = midpointSignChanges;
                if (midpointSignChanges < 0) {
                    midpointSignChanges = -1 - midpointSignChanges;
                    adjustedMidpointSignChanges = midpointSignChanges;
                    if (midpointSignChanges >= maxSignChanges && midpointSignChanges < minSignChanges) {
                        solutions[solutionIndex++] = midpoint;
                        adjustedMidpointSignChanges = midpointSignChanges + 1;
                    }
                }

                if (adjustedMidpointSignChanges > maxSignChanges) {
                    int delta = adjustedMidpointSignChanges - maxSignChanges;
                    midpointSignChanges -= delta;
                    adjustedMidpointSignChanges -= delta;
                }

                if (midpointSignChanges < minSignChanges) {
                    int delta = minSignChanges - midpointSignChanges;
                    midpointSignChanges += delta;
                    adjustedMidpointSignChanges += delta;
                }

                solutionIndex = isolateRoots(sturmSequence, sequenceLength, min, minSignChanges, midpoint,
                        adjustedMidpointSignChanges, solutionIndex, solutions);
                solutionIndex = isolateRoots(sturmSequence, sequenceLength, midpoint, midpointSignChanges, max,
                        maxSignChanges, solutionIndex, solutions);
            } else {
                solutions[solutionIndex++] = midpoint;
            }
        }

        return solutionIndex;
    }

    /// Compacts roots in-place so the returned prefix contains only solutions in `[min, max]`.
    private static int filterRootsToRange(int solutionCount, double[] solutions, double min, double max) {
        int keptSolutionCount = 0;

        for (int solutionIndex = 0; solutionIndex < solutionCount; ++solutionIndex)
            if (solutions[solutionIndex] >= min && solutions[solutionIndex] <= max)
                solutions[keptSolutionCount++] = solutions[solutionIndex];

        return keptSolutionCount;
    }

    /// Solves a polynomial on a bounded interval with a Sturm sequence when direct solvers do not
    /// apply.
    ///
    /// The helper also removes repeated factors before delegating back to the linear, quadratic, or
    /// cubic closed-form solvers.
    private static int solveWithSturmSequence(int degree, double[] coefficients, double min, double max,
                                              double[] solutions) {
        assert degree > 0;
        assert coefficients[degree] != 0.0D;

        double maxAbsCoefficient = 0.0D;
        for (int coefficientIndex = 0; coefficientIndex < degree; ++coefficientIndex)
            maxAbsCoefficient = Math.max(maxAbsCoefficient, Math.abs(coefficients[coefficientIndex]));

        double cauchyBound = maxAbsCoefficient / Math.abs(coefficients[degree]) + 1.0D;
        if (min < -cauchyBound)
            min = -cauchyBound;
        if (max > cauchyBound)
            max = cauchyBound;

        if (min >= max) {
            if (min == max && evaluatePolynomial(coefficients, min) == 0.0D) {
                solutions[0] = min;
                return 1;
            }
            return 0;
        }

        if (coefficients.length > degree + 1) {
            double[] trimmedCoefficients = new double[degree + 1];
            System.arraycopy(coefficients, 0, trimmedCoefficients, 0, degree + 1);
            coefficients = trimmedCoefficients;
        }

        outer:
        while (true) {
            double[][] sturmSequence = new double[degree + 1][];
            int sequenceLength = 0;
            sturmSequence[sequenceLength++] = coefficients;
            double[] derivativeCoefficients = new double[degree];

            for (int coefficientIndex = 0; coefficientIndex < degree; ++coefficientIndex)
                derivativeCoefficients[coefficientIndex] = coefficients[coefficientIndex + 1] * (coefficientIndex + 1);

            sturmSequence[sequenceLength++] = derivativeCoefficients;

            while (true) {
                derivativeCoefficients = computeNegatedRemainder(sturmSequence[sequenceLength - 2],
                        sturmSequence[sequenceLength - 1]);
                if (derivativeCoefficients.length == 0) {
                    if (sturmSequence[sequenceLength - 1].length <= 1) {
                        int solutionIndex = 0;
                        int minSignChanges = countSturmSignChanges(sturmSequence, sequenceLength, min);
                        if (minSignChanges < 0) {
                            minSignChanges = -1 - minSignChanges;
                            solutions[solutionIndex++] = min;
                        }

                        int maxSignChanges = countSturmSignChanges(sturmSequence, sequenceLength, max);
                        if (maxSignChanges < 0) {
                            maxSignChanges = -1 - maxSignChanges;
                            if (maxSignChanges < minSignChanges) {
                                solutions[solutionIndex++] = max;
                                ++maxSignChanges;
                            }
                        }

                        return isolateRoots(sturmSequence, sequenceLength, min, minSignChanges, max,
                                maxSignChanges, solutionIndex, solutions);
                    }

                    coefficients = dividePolynomial(coefficients, sturmSequence[sequenceLength - 1]);
                    degree = coefficients.length - 1;
                    switch (degree) {
                        case 1:
                            return filterRootsToRange(
                                    solveLinear(coefficients[1], coefficients[0], solutions),
                                    solutions, min, max);
                        case 2:
                            return filterRootsToRange(
                                    solveQuadratic(coefficients[2], coefficients[1], coefficients[0], solutions),
                                    solutions, min, max);
                        case 3:
                            return filterRootsToRange(
                                    solveCubic(coefficients[3], coefficients[2], coefficients[1], coefficients[0], solutions),
                                    solutions, min, max);
                        default:
                            continue outer;
                    }
                }

                sturmSequence[sequenceLength++] = derivativeCoefficients;
            }
        }
    }

    /// Computes the next polynomial in the Sturm sequence as the negated remainder of a division.
    private static double[] computeNegatedRemainder(double[] dividendCoefficients, double[] divisorCoefficients) {
        int dividendDegree = dividendCoefficients.length - 1;
        int divisorDegree = divisorCoefficients.length - 1;
        double[] remainderCoefficients = new double[dividendDegree + 1];
        System.arraycopy(dividendCoefficients, 0, remainderCoefficients, 0, dividendDegree + 1);

        while (dividendDegree >= divisorDegree) {
            double quotientCoefficient = remainderCoefficients[dividendDegree] / divisorCoefficients[divisorDegree];

            for (int divisorIndex = 0; divisorIndex < divisorDegree; ++divisorIndex)
                remainderCoefficients[divisorIndex + dividendDegree - divisorDegree] -=
                        quotientCoefficient * divisorCoefficients[divisorIndex];

            --dividendDegree;
        }

        while (dividendDegree >= 0 && remainderCoefficients[dividendDegree] == 0.0D)
            --dividendDegree;

        double[] negatedRemainder = new double[dividendDegree + 1];
        for (int coefficientIndex = 0; coefficientIndex <= dividendDegree; ++coefficientIndex)
            negatedRemainder[coefficientIndex] = -remainderCoefficients[coefficientIndex];

        return negatedRemainder;
    }

    private static double realCubicRoot(double value) {
        return Math.copySign(Math.pow(Math.abs(value), 0.3333333333333333D), value);
    }

    /// Solves `cubic*x^3 + quadratic*x^2 + linear*x + constant = 0` for real roots.
    ///
    /// Complex roots are ignored. If `cubic` is zero the method delegates to
    /// [#solveQuadratic(double, double, double, double[])].
    public static int solveCubic(double cubic, double quadratic, double linear, double constant, double[] solutions) {
        if (cubic == 0.0D)
            return solveQuadratic(quadratic, linear, constant, solutions);

        double offset = quadratic / (3.0D * cubic);
        double threeCubicSquared = 3.0D * cubic * cubic;
        double depressedQuadratic = (3.0D * cubic * linear - quadratic * quadratic) / threeCubicSquared;
        double depressedConstant = (2.0D * quadratic * quadratic * quadratic
                - 9.0D * cubic * quadratic * linear
                + 9.0D * threeCubicSquared * constant) / (9.0D * threeCubicSquared * cubic);
        if (depressedQuadratic == 0.0D) {
            solutions[0] = -realCubicRoot(depressedConstant) - offset;
            return 1;
        }

        double depressedQuadraticCubedOver27 = depressedQuadratic * depressedQuadratic * depressedQuadratic / 27.0D;
        double depressedConstantSquaredOver4 = depressedConstant * depressedConstant / 4.0D;
        double discriminant = depressedQuadraticCubedOver27 + depressedConstantSquaredOver4;
        if (discriminant < 0.0D) {
            double radius = Math.sqrt(-depressedQuadraticCubedOver27);
            double angle = Math.acos(-depressedConstant / (2.0D * radius));
            double amplitude = 2.0D * realCubicRoot(radius);
            solutions[0] = amplitude * Math.cos(angle / 3.0D) - offset;
            solutions[1] = amplitude * Math.cos((angle + 6.283185307179586D) / 3.0D) - offset;
            solutions[2] = amplitude * Math.cos((angle + 12.566370614359172D) / 3.0D) - offset;
            return 3;
        }

        double sumTerm = depressedConstant > 0.0D
                ? depressedQuadraticCubedOver27 / (Math.sqrt(discriminant) + depressedConstant / 2.0D)
                : Math.sqrt(discriminant) - depressedConstant / 2.0D;
        double firstRootPart = realCubicRoot(sumTerm);
        if (firstRootPart == 0.0D) {
            solutions[0] = -realCubicRoot(depressedConstant) - offset;
            return 1;
        }

        double secondRootPart = -depressedQuadratic / (3.0D * firstRootPart);
        if (firstRootPart == secondRootPart) {
            solutions[0] = firstRootPart + secondRootPart - offset;
            solutions[1] = -0.5D * (firstRootPart + secondRootPart) - offset;
            return 2;
        }

        solutions[0] = firstRootPart + secondRootPart - offset;
        return 1;
    }

    /// Solves `linear*x + constant = 0`.
    public static int solveLinear(double linear, double constant, double[] solutions) {
        if (linear == 0.0D)
            return constant == 0.0D ? -1 : 0;

        solutions[0] = -constant / linear;
        return 1;
    }

    /// Solves a polynomial of up to arbitrary degree and keeps only roots in `[min, max]`.
    ///
    /// Leading zero coefficients are ignored before the degree is interpreted. For degrees above
    /// three the implementation isolates real roots with a Sturm sequence.
    public static int solvePolynomial(int degree, double[] coefficients, double min, double max, double[] solutions) {
        while (degree > 0 && coefficients[degree] == 0.0D)
            --degree;

        switch (degree) {
            case 0:
                return coefficients[0] == 0.0D ? -1 : 0;
            case 1:
                return filterRootsToRange(solveLinear(coefficients[1], coefficients[0], solutions), solutions, min, max);
            case 2:
                return filterRootsToRange(
                        solveQuadratic(coefficients[2], coefficients[1], coefficients[0], solutions),
                        solutions, min, max);
            case 3:
                return filterRootsToRange(
                        solveCubic(coefficients[3], coefficients[2], coefficients[1], coefficients[0], solutions),
                        solutions, min, max);
            default:
                return solveWithSturmSequence(degree, coefficients, min, max, solutions);
        }
    }

    /// Solves a polynomial of up to arbitrary degree on the full real line.
    public static int solvePolynomial(int degree, double[] coefficients, double[] solutions) {
        while (degree > 0 && coefficients[degree] == 0.0D)
            --degree;

        switch (degree) {
            case 0:
                return coefficients[0] == 0.0D ? -1 : 0;
            case 1:
                return solveLinear(coefficients[1], coefficients[0], solutions);
            case 2:
                return solveQuadratic(coefficients[2], coefficients[1], coefficients[0], solutions);
            case 3:
                return solveCubic(coefficients[3], coefficients[2], coefficients[1], coefficients[0], solutions);
            default:
                return solveWithSturmSequence(degree, coefficients, -Double.MAX_VALUE, Double.MAX_VALUE, solutions);
        }
    }

    /// Solves `quadratic*x^2 + linear*x + constant = 0` for real roots.
    ///
    /// Complex roots are ignored. If `quadratic` is zero the method delegates to
    /// [#solveLinear(double, double, double[])].
    public static int solveQuadratic(double quadratic, double linear, double constant, double[] solutions) {
        if (quadratic == 0.0D)
            return solveLinear(linear, constant, solutions);

        double discriminant = linear * linear - 4.0D * quadratic * constant;
        if (discriminant < 0.0D)
            return 0;
        if (discriminant == 0.0D) {
            solutions[0] = -linear / (2.0D * quadratic);
            return 1;
        }

        double squareRoot = Math.sqrt(discriminant);
        solutions[0] = (squareRoot - linear) / (2.0D * quadratic);
        solutions[1] = (-squareRoot - linear) / (2.0D * quadratic);
        return 2;
    }
}
