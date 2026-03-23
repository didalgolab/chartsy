package one.chartsy.charting.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class IntIntervalSetTest {
    private static final int RANDOM_SCENARIO_COUNT = 250;
    private static final int RANDOM_OPERATION_COUNT = 64;

    private static final int[] BOUNDARY_POINTS = {
            Integer.MIN_VALUE,
            Integer.MIN_VALUE + 1,
            Integer.MIN_VALUE + 2,
            -1,
            0,
            1,
            Integer.MAX_VALUE - 2,
            Integer.MAX_VALUE - 1,
            Integer.MAX_VALUE
    };
    private static final Interval[] BOUNDARY_INTERVALS = intervalsFrom(BOUNDARY_POINTS);

    private static final int[] MULTI_INTERVAL_POINTS = {
            Integer.MIN_VALUE,
            Integer.MIN_VALUE + 1,
            0,
            1,
            Integer.MAX_VALUE - 1,
            Integer.MAX_VALUE
    };
    private static final Interval[] MULTI_INTERVAL_INTERVALS = intervalsFrom(MULTI_INTERVAL_POINTS);

    @Test
    void single_point_boundaries_round_trip_without_overflow() {
        assertContract(
                Operation.add(Integer.MIN_VALUE, Integer.MIN_VALUE),
                Operation.remove(Integer.MIN_VALUE, Integer.MIN_VALUE)
        );
        assertContract(
                Operation.add(Integer.MAX_VALUE, Integer.MAX_VALUE),
                Operation.remove(Integer.MAX_VALUE, Integer.MAX_VALUE)
        );
    }

    @Test
    void adjacent_boundary_intervals_merge_without_overflow() {
        assertContract(
                Operation.add(Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 1),
                Operation.add(Integer.MIN_VALUE, Integer.MIN_VALUE)
        );
        assertContract(
                Operation.add(Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1),
                Operation.add(Integer.MAX_VALUE, Integer.MAX_VALUE)
        );
    }

    @Test
    void add_contract_matches_reference_model_for_boundary_rich_interval_pairs() {
        for (Interval first : BOUNDARY_INTERVALS) {
            for (Interval second : BOUNDARY_INTERVALS) {
                assertContract(Operation.add(first), Operation.add(second));
            }
        }
    }

    @Test
    void remove_contract_matches_reference_model_for_boundary_rich_single_interval_ranges() {
        for (Interval existing : BOUNDARY_INTERVALS) {
            for (Interval removed : BOUNDARY_INTERVALS) {
                assertContract(Operation.add(existing), Operation.remove(removed));
            }
        }
    }

    @Test
    void remove_contract_matches_reference_model_for_multi_interval_ranges() {
        for (Interval first : MULTI_INTERVAL_INTERVALS) {
            for (Interval second : MULTI_INTERVAL_INTERVALS) {
                for (Interval removed : MULTI_INTERVAL_INTERVALS) {
                    assertContract(Operation.add(first), Operation.add(second), Operation.remove(removed));
                }
            }
        }
    }

    @Test
    void clear_contract_matches_reference_model() {
        assertContract(
                Operation.add(Integer.MIN_VALUE, Integer.MIN_VALUE + 2),
                Operation.add(-10, 10),
                Operation.remove(-3, 3),
                Operation.clear(),
                Operation.add(Integer.MAX_VALUE - 1, Integer.MAX_VALUE)
        );
    }

    @Test
    void iterator_remove_deletes_exactly_the_returned_intervals() {
        var contract = new ContractState();
        var setup = List.of(
                Operation.add(Integer.MIN_VALUE, Integer.MIN_VALUE + 2),
                Operation.add(-10, -5),
                Operation.add(-2, 4),
                Operation.add(Integer.MAX_VALUE - 2, Integer.MAX_VALUE)
        );
        contract.applyAll(setup);

        var removedIntervals = new ArrayList<Interval>();
        var iterator = contract.actual().intervalIterator();
        while (iterator.hasNext()) {
            IntInterval interval = iterator.next();
            removedIntervals.add(toInterval(interval));
            contract.expected().remove(interval.getFirst(), interval.getLast());
            iterator.remove();
            assertMatchesReferenceModel(contract.actual(), contract.expected(),
                    "iterator.remove after " + removedIntervals);
        }

        assertThat(contract.actual().size()).isZero();
        assertThat(snapshotIntervals(contract.actual())).isEmpty();
        assertThat(removedIntervals).containsExactly(
                new Interval(Integer.MIN_VALUE, Integer.MIN_VALUE + 2),
                new Interval(-10, -5),
                new Interval(-2, 4),
                new Interval(Integer.MAX_VALUE - 2, Integer.MAX_VALUE)
        );
    }

    @Test
    void random_operation_sequences_match_reference_model() {
        for (int seed = 0; seed < RANDOM_SCENARIO_COUNT; seed++) {
            assertContract(randomOperations(seed));
        }
    }

    private static void assertContract(Operation... operations) {
        var contract = new ContractState(operations.length);
        for (Operation operation : operations) {
            contract.apply(operation);
            contract.assertMatches();
        }
    }

    private static void assertMatchesReferenceModel(IntIntervalSet actual, ReferenceIntervalSet expected,
                                                    List<Operation> scenario) {
        assertMatchesReferenceModel(actual, expected, scenario.toString(), probeValues(expected.intervals(), scenario));
    }

    private static void assertMatchesReferenceModel(IntIntervalSet actual, ReferenceIntervalSet expected,
                                                    String scenarioLabel) {
        assertMatchesReferenceModel(actual, expected, scenarioLabel, probeValues(expected.intervals()));
    }

    private static void assertMatchesReferenceModel(IntIntervalSet actual, ReferenceIntervalSet expected,
                                                    String scenarioLabel, List<Integer> probes) {
        var expectedIntervals = expected.intervals();
        var actualIntervals = snapshotIntervals(actual);

        assertIntervalSetInvariants(actualIntervals, scenarioLabel);
        assertThat(actualIntervals)
                .as("intervals after %s", scenarioLabel)
                .isEqualTo(expectedIntervals);
        assertThat(actual.size())
                .as("size after %s", scenarioLabel)
                .isEqualTo(expectedIntervals.size());

        for (int probe : probes) {
            assertThat(actual.contains(probe))
                    .as("contains(%s) after %s", probe, scenarioLabel)
                    .isEqualTo(expected.contains(probe));
        }
    }

    private static void assertIntervalSetInvariants(List<Interval> intervals, String scenarioLabel) {
        for (int index = 0; index < intervals.size(); index++) {
            Interval interval = intervals.get(index);
            assertThat(interval.last())
                    .as("non-empty interval %s after %s", interval, scenarioLabel)
                    .isGreaterThanOrEqualTo(interval.first());
            if (index == 0) {
                continue;
            }

            Interval previous = intervals.get(index - 1);
            assertThat((long) interval.first())
                    .as("sorted disjoint intervals after %s", scenarioLabel)
                    .isGreaterThan(((long) previous.last()) + 1L);
        }
    }

    private static List<Integer> probeValues(List<Interval> intervals) {
        var probes = new LinkedHashSet<Integer>();
        for (int point : BOUNDARY_POINTS) {
            probes.add(point);
        }
        for (Interval interval : intervals) {
            addIntervalProbes(probes, interval);
        }
        return List.copyOf(probes);
    }

    private static List<Integer> probeValues(List<Interval> intervals, List<Operation> operations) {
        var probes = new LinkedHashSet<>(probeValues(intervals));
        for (Operation operation : operations) {
            if (operation.hasRange()) {
                addIntervalProbes(probes, operation.interval());
            }
        }
        return List.copyOf(probes);
    }

    private static void addIntervalProbes(LinkedHashSet<Integer> probes, Interval interval) {
        addProbe(probes, interval.first());
        addProbe(probes, interval.last());
        addProbe(probes, midpoint(interval));
        if (interval.first() != Integer.MIN_VALUE) {
            addProbe(probes, interval.first() - 1);
        }
        if (interval.first() != Integer.MAX_VALUE) {
            addProbe(probes, interval.first() + 1);
        }
        if (interval.last() != Integer.MIN_VALUE) {
            addProbe(probes, interval.last() - 1);
        }
        if (interval.last() != Integer.MAX_VALUE) {
            addProbe(probes, interval.last() + 1);
        }
    }

    private static void addProbe(LinkedHashSet<Integer> probes, int value) {
        probes.add(value);
    }

    private static int midpoint(Interval interval) {
        return (int) ((((long) interval.first()) + interval.last()) / 2L);
    }

    private static Interval toInterval(IntInterval interval) {
        return new Interval(interval.getFirst(), interval.getLast());
    }

    private static List<Interval> snapshotIntervals(IntIntervalSet set) {
        var intervals = new ArrayList<Interval>();
        var iterator = set.intervalIterator();
        while (iterator.hasNext()) {
            intervals.add(toInterval(iterator.next()));
        }
        return intervals;
    }

    private static Interval[] intervalsFrom(int[] points) {
        var intervals = new ArrayList<Interval>();
        for (int firstIndex = 0; firstIndex < points.length; firstIndex++) {
            for (int lastIndex = firstIndex; lastIndex < points.length; lastIndex++) {
                intervals.add(new Interval(points[firstIndex], points[lastIndex]));
            }
        }
        return intervals.toArray(Interval[]::new);
    }

    private static Operation[] randomOperations(long seed) {
        var random = new SplittableRandom(seed);
        var operations = new Operation[RANDOM_OPERATION_COUNT];
        for (int index = 0; index < operations.length; index++) {
            int operationKind = random.nextInt(10);
            if (operationKind == 0) {
                operations[index] = Operation.clear();
                continue;
            }

            int first = randomValue(random);
            int last = randomValue(random);
            if (random.nextBoolean()) {
                int lowerBound = Math.min(first, last);
                int upperBound = Math.max(first, last);
                first = lowerBound;
                last = upperBound;
            }
            operations[index] = (operationKind <= 5) ? Operation.add(first, last) : Operation.remove(first, last);
        }
        return operations;
    }

    private static int randomValue(SplittableRandom random) {
        return (random.nextInt(4) == 0)
                ? BOUNDARY_POINTS[random.nextInt(BOUNDARY_POINTS.length)]
                : random.nextInt();
    }

    private record Interval(int first, int last) {
        @Override
        public String toString() {
            return "[" + first + "," + last + "]";
        }
    }

    private enum OperationKind {
        ADD,
        REMOVE,
        CLEAR
    }

    private record Operation(OperationKind kind, int first, int last) {

        static Operation add(int first, int last) {
            return new Operation(OperationKind.ADD, first, last);
        }

        static Operation add(Interval interval) {
            return add(interval.first(), interval.last());
        }

        static Operation remove(int first, int last) {
            return new Operation(OperationKind.REMOVE, first, last);
        }

        static Operation remove(Interval interval) {
            return remove(interval.first(), interval.last());
        }

        static Operation clear() {
            return new Operation(OperationKind.CLEAR, 0, 0);
        }

        boolean hasRange() {
            return kind != OperationKind.CLEAR;
        }

        Interval interval() {
            return new Interval(first, last);
        }

        void apply(IntIntervalSet set) {
            switch (kind) {
                case ADD -> set.add(first, last);
                case REMOVE -> set.remove(first, last);
                case CLEAR -> set.clear();
            }
        }

        void apply(ReferenceIntervalSet set) {
            switch (kind) {
                case ADD -> set.add(first, last);
                case REMOVE -> set.remove(first, last);
                case CLEAR -> set.clear();
            }
        }

        @Override
        public String toString() {
            return switch (kind) {
                case ADD -> "add[" + first + "," + last + "]";
                case REMOVE -> "remove[" + first + "," + last + "]";
                case CLEAR -> "clear";
            };
        }
    }

    private static final class ReferenceIntervalSet {
        private List<Interval> intervals = List.of();

        void add(int first, int last) {
            if (first > last) {
                return;
            }

            long mergedFirst = first;
            long mergedLast = last;
            var mergedIntervals = new ArrayList<Interval>();
            boolean inserted = false;

            for (Interval interval : intervals) {
                long intervalFirst = interval.first();
                long intervalLast = interval.last();
                if (intervalLast + 1L < mergedFirst) {
                    mergedIntervals.add(interval);
                    continue;
                }
                if (mergedLast + 1L < intervalFirst) {
                    if (!inserted) {
                        mergedIntervals.add(new Interval((int) mergedFirst, (int) mergedLast));
                        inserted = true;
                    }
                    mergedIntervals.add(interval);
                    continue;
                }

                mergedFirst = Math.min(mergedFirst, intervalFirst);
                mergedLast = Math.max(mergedLast, intervalLast);
            }

            if (!inserted) {
                mergedIntervals.add(new Interval((int) mergedFirst, (int) mergedLast));
            }
            intervals = List.copyOf(mergedIntervals);
        }

        void clear() {
            intervals = List.of();
        }

        void remove(int first, int last) {
            if (first > last) {
                return;
            }

            var remainingIntervals = new ArrayList<Interval>();

            for (Interval interval : intervals) {
                if (interval.last() < first || interval.first() > last) {
                    remainingIntervals.add(interval);
                    continue;
                }
                if (interval.first() < first) {
                    remainingIntervals.add(new Interval(interval.first(), (int) (((long) first) - 1L)));
                }
                if (last < interval.last()) {
                    remainingIntervals.add(new Interval((int) (((long) last) + 1L), interval.last()));
                }
            }

            intervals = List.copyOf(remainingIntervals);
        }

        boolean contains(int value) {
            for (Interval interval : intervals) {
                if (interval.first() <= value && value <= interval.last()) {
                    return true;
                }
            }
            return false;
        }

        List<Interval> intervals() {
            return intervals;
        }
    }

    private static final class ContractState {
        private final IntIntervalSet actual = new IntIntervalSet();
        private final ReferenceIntervalSet expected = new ReferenceIntervalSet();
        private final List<Operation> scenario;

        ContractState() {
            this(0);
        }

        ContractState(int initialScenarioCapacity) {
            scenario = new ArrayList<>(initialScenarioCapacity);
        }

        IntIntervalSet actual() {
            return actual;
        }

        ReferenceIntervalSet expected() {
            return expected;
        }

        void apply(Operation operation) {
            scenario.add(operation);
            operation.apply(actual);
            operation.apply(expected);
        }

        void applyAll(Iterable<Operation> operations) {
            for (Operation operation : operations) {
                apply(operation);
            }
        }

        void assertMatches() {
            assertMatchesReferenceModel(actual, expected, scenario);
        }
    }
}
