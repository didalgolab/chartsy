/*
 * Copyright 2007 Flaptor (flaptor.com)
 * Copyright 2021 Mariusz (chartsy.one)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package one.chartsy.data.structures;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A histogram that adapts to an unknown data distribution.
 *
 * It keeps more or less constant resolution throughout the data range by increasing
 * the resolution where the data is denser. For example, if the data has such
 * distribution that most of the values lie in the 0-5 range and only a few are
 * in the 5-10 range, the histogram would adapt and assign more counting buckets to
 * the 0-5 range and less to the 5-10 range.
 *
 * This implementation provides a method to obtain the accumulative density function 
 * for a given data point, and a method to obtain the data point that splits the 
 * data set at a given percentile.
 *
 * @author Jorge Handl
 * @author Mariusz Bernacki
 */
public class AdaptiveHistogram implements Serializable {
    /** The total number of data points. */
    private long totalCount;
    /** The root of the histogram's data tree. */
    private Node root = new LeafNode();

    /**
     * Erases all data from the histogram.
     */
    public void reset() {
        root.reset();
        root = new LeafNode();
        totalCount = 0;
    }

    /**
     * Adds a data point to the histogram.
     *
     * @param value the data point to add
     */
    public synchronized void addValue(double value) {
        totalCount++;
        root = root.addValue(this, value);
    }

    /**
     * Gives the number of data points stored in the same bucket as a given value.
     *
     * @param value the reference data point
     * @return the number of data points stored in the same bucket as the reference point
     */
    public long getCount(double value) {
        return root.getCount(value);
    }

    /**
     * Gives the cumulative density function for a given data point.
     *
     * @param value the reference data point
     * @return the cumulative density function for the reference point
     */
    public long getAccumCount(double value) {
        return root.getAccumCount(value);
    }

    /**
     * Returns the data point that splits the data set at a given percentile.
     *
     * @param percentile the percentile at which the data set is split
     * @return the data point that splits the data set at the given percentile
     */
    public double getValueForPercentile(int percentile) {
        var targetAccumCount = (totalCount * percentile) / 100;
        return root.getValueForAccumCount(new long[] {0, targetAccumCount});
    }

    /**
     * This method is used by the internal data structure of the histogram to get the
     * limit of data points that should be counted at one bucket.
     *
     * @return the limit of data points to store a one bucket
     */
    protected int getCountPerNodeLimit() {
        int limit = (int) (totalCount / 10);
        if (limit == 0)
            limit = 1;

        return limit;
    }

    /**
     * Auxiliary interface for inline functor object.
     */
    @FunctionalInterface
    protected interface ValueConversion {
        /**
         * This method should implement the conversion function.
         *
         * @param value the input value
         * @return the resulting converted value
         */
        double convertValue(double value);
    }

    /**
     * Normalizes all the values to the desired range.
     *
     * @param targetMin the target new minimum value
     * @param targetMax the target new maximum value
     */
    public void normalize(double targetMin, double targetMax) {
        var min = getValueForPercentile(0);
        var max = getValueForPercentile(100);
        var m = (targetMax - targetMin) * ((max > min) ? 1 / (max - min) : 1);
        var b = targetMin;

        root.apply(value -> m * (value - min) + b);
    }

    /**
     * Shows the histograms' underlying data structure.
     */
    public void show() {
        System.out.println("Histogram has " + totalCount + " values:");
        root.show(0);
    }

    /**
     * Return a table representing the data in this histogram.
     * Each element is a table cell containing the range limit values and the count for that range.
     */
    public List<Cell> toTable() {
        List<Cell> table = new ArrayList<>();
        root.toTable(table);

        return table;
    }

    /**
     * A HistogramNode is the building block for the histogram data structure and
     * provides a common interface for both the data and the fork nodes.
     * This is an abstract class, and it is instantiated as either a fork node or a data node.
     */
    public static abstract class Node implements Serializable {

        /** Abstract method for clearing the node */
        public abstract void reset();

        /** Abstract method for adding a value to the histogram */
        public abstract Node addValue(AdaptiveHistogram root, double value);

        /** Abstract method for getting the number of values stored in the same bucket as a reference value */
        public abstract long getCount(double value);

        /** Abstract method for getting the cumulative density function for a given value */
        public abstract long getAccumCount(double value);

        /** Abstract method for getting the value for which the cumulative density function reaches the desired value */
        public abstract Double getValueForAccumCount(long[] accumCount);

        /** Abstract method for applying a conversion function to the values stored in the histogram */
        public abstract void apply(AdaptiveHistogram.ValueConversion valueConversion);

        /** Abstract method for getting a table representing the histogram data */
        public abstract void toTable(List<Cell> table);

        /** Abstract method for showing the data structure */
        public abstract void show(int level);

        /** Prints a margin corresponding to the provided tree level */
        protected void margin(int level) {
            for (int i = 0; i < level; i++) {
                System.out.print("  ");
            }
        }
    }

    /**
     * The HistogramDataNode stores the histogram data for a range of values.
     * It knows the minimum and maximum values for which it counts the number of instances.
     * When the count exceeds the allowed limit it splits itself in two, increasing the
     * histogram resolution for this range.
     */
    public static class LeafNode extends Node {

        /** Attributes of a data node. */
        private final Cell cell = new Cell();

        /**
         * Creates an empty data node.
         */
        public LeafNode() {
            reset();
        }

        /**
         * Creates a data node for the given range with the given instance count.
         *
         * @param count the number of data instances in the given range
         * @param minValue the start of the range of counted values
         * @param maxValue the end of the range of counted values
         */
        public LeafNode(long count, double minValue, double maxValue) {
            reset();
            cell.count = count;
            cell.minValue = minValue;
            cell.maxValue = maxValue;
        }

        /**
         * Clears the data node.
         */
        @Override
        public void reset() {
            cell.count = 0;
            cell.minValue = Double.MAX_VALUE;
            cell.maxValue = -Double.MAX_VALUE;
        }

        /**
         * Adds a value to the data node.<p>
         * If the value falls inside of the nodes' range and the count does not exceed the imposed limit, it simply increments the count.<br>
         * If the value falls outside of the nodes' range, it expands the range.<br>
         * If the count exceeds the limit, it splits in two assuming uniform distribution inside the node.<br>
         * If the value falls outside of the nodes' range AND the count exceeds the limit, it creates a new node for that value.
         *
         * @param root a reference to the adaptive histogram instance that uses this structure
         * @param value the value for which the count is to be incremented
         * @return A reference to itself if no structural change happened, or a reference to the new fork node if this node was split
         */
        @Override
        public Node addValue(AdaptiveHistogram root, double value) {
            // "self" is what is returned to the caller. If this node needs to be replaced by a fork node,
            // this variable will hold the new fork node and it will be returned to the caller.
            // Otherwise, the node returned will be this, in which case nothing changes.
            Node self = this;
            if (value >= cell.minValue && value <= cell.maxValue) {  // the value falls within this nodes' range
                if (cell.count < root.getCountPerNodeLimit()  // there is enough room in this node for the new value
                        || cell.minValue == cell.maxValue) {  // or the node defines a zero-width range so it can't be split
                    cell.count++;
                } else {  // not enough room, distribute the value count among the new nodes, assuming uniform distribution
                    double splitValue = (cell.minValue + cell.maxValue) / 2;
                    long rightCount = cell.count / 2;
                    long leftCount = rightCount;
                    boolean countWasOdd = (leftCount + rightCount < cell.count);
                    // assign the new value to the corresponding side. If the count is odd, add the extra item to the other side to keep balance
                    if (value > splitValue) {
                        rightCount++;
                        leftCount += (countWasOdd?1:0);
                    } else {
                        leftCount++;
                        rightCount += (countWasOdd?1:0);
                    }
                    // create a new subtree that will replace this node
                    Node leftNode = new LeafNode(leftCount, cell.minValue, splitValue);
                    Node rightNode = new LeafNode(rightCount, splitValue, cell.maxValue);
                    self = new ForkNode(splitValue, leftNode, rightNode);
                }
            } else {  // the value falls outside of this nodes' range
                if (cell.count < root.getCountPerNodeLimit()) {  // there is enough room in this node for the new value
                    cell.count++;
                    // extend the range of this node, assuming that the tree structure above correctly directed
                    // the given value to this node and therefore it lies at one of the borders of the tree.
                    if (value < cell.minValue) cell.minValue = value;
                    if (value > cell.maxValue) cell.maxValue = value;
                } else {  // not enough room, create a new sibling node for the new value and put both under a new fork node
                    if (value < cell.minValue) {
                        cell.minValue = Math.min(cell.minValue, (value + cell.maxValue) / 2);
                        self = new ForkNode(cell.minValue, new LeafNode(1,value,cell.minValue), this);
                    } else {
                        cell.maxValue = Math.max(cell.maxValue, (cell.minValue + value) / 2);
                        self = new ForkNode(cell.maxValue, this, new LeafNode(1,cell.maxValue,value));
                    }
                }
            }
            return self;
        }

        /**
         * Returns the number of data points stored in the same bucket as a given value.
         * @param value the reference data point.
         * @return the number of data points stored in the same bucket as the reference point.
         */
        @Override
        public long getCount(double value) {
            long res = 0;
            if (value >= cell.minValue && value <= cell.maxValue) {
                res = cell.count;
            }
            return res;
        }

        /**
         * Returns the cumulative density function for a given data point.
         * @param value the reference data point.
         * @return the cumulative density function for the reference point.
         */
        @Override
        public long getAccumCount(double value) {
            long res = 0;
            if (value >= cell.minValue) {
                res = cell.count;
            }
            return res;
        }

        // Linear interpolation for double values.
        private double interpolate(double x0, double y0, double x1, double y1, double x) {
            return y0+((x-x0)*(y1-y0))/(x1-x0);
        }

        /**
         * Returns the data point where the running cumulative count reaches the target cumulative count.
         * It uses linear interpolation over the range of the node to get a better estimate of the true value.
         *
         * @param accumCount an array containing:<br>
         *      - accumCount[0] the running cumulative count <br>
         *      - accumCount[1] the target cumulative count
         * @return the data point where the running cumulative count reaches the target cumulative count
         */
        @Override
        public Double getValueForAccumCount(long[] accumCount) {
            Double res = null;
            long runningAccumCount = accumCount[0];
            long targetAccumCount = accumCount[1];
            if (runningAccumCount <= targetAccumCount && runningAccumCount + cell.count >= targetAccumCount) {
                res = interpolate((double)runningAccumCount, cell.minValue, (double)(runningAccumCount + cell.count), cell.maxValue, (double)targetAccumCount);
            }
            accumCount[0] += cell.count;
            return res;
        }

        /**
         * Applies a convertion function to the values stored in the histogram.
         * @param valueConversion a class that defines a function to convert the value.
         */
        @Override
        public void apply(AdaptiveHistogram.ValueConversion valueConversion) {
            cell.minValue = valueConversion.convertValue(cell.minValue);
            cell.maxValue = valueConversion.convertValue(cell.maxValue);
        }

        /**
         * Prints this nodes' data with a margin depending on the level of the node in the tree.
         * @param level the level of this node in the tree.
         */
        @Override
        public void show(int level) {
            margin(level);
            System.out.println("Data: " + cell.count + " ("+cell.minValue+","+cell.maxValue+")");
        }

        /**
         * Build the table representing the histogram data adding this node's cell to it.
         */
        @Override
        public void toTable(List<Cell> table) {
            table.add(cell);
        }
    }

    /**
     * The HistogramForkNode splits the data range in two at a given value, pointing to two subtrees,
     * one for values smaller than the split value, and one for values larger than the split value.
     * It implements the recursive calls necesary to obtain the data from the tree structure.
     */
    public static class ForkNode extends Node {

        private double splitValue;
        private Node left;
        private Node right;

        public ForkNode(double splitValue, Node left, Node right) {
            this.splitValue = splitValue;
            this.left = left;
            this.right = right;
        }

        /**
         * Clears the fork node, recursively erasing the subtrees.
         */
        @Override
        public void reset() {
            if (left != null) {
                left.reset();
                left = null;
            }
            if (right != null) {
                right.reset();
                right = null;
            }
            splitValue = 0;
        }

        /**
         * Adds a value to the histogram by recursively adding the value to either subtree, depending on the split value.
         *
         * @param root a reference to the adaptive histogram instance that uses this structure
         * @param value the value for which the count is to be incremented
         * @return A reference to itself
         */
        @Override
        public Node addValue(AdaptiveHistogram root, double value) {
            // The data node addValue implementation returns a reference to itself if there was no structural change needed,
            // or a reference to a new fork node if the data node had to be split in two. By assigning the returned reference
            // to the corresponding subtree variable (left or right), the subtree can replace itself with a new structure,
            // eliminating the need for a node to manipulate its subtree, for which it would need to know a lot about what
            // happens at the lower level.
            if (value > splitValue) {
                right = right.addValue(root, value);
            } else {
                left = left.addValue(root, value);
            }
            return this;
        }

        /**
         * Returns the number of data points stored in the same bucket as a given value.
         * @param value the reference data point.
         * @return the number of data points stored in the same bucket as the reference point.
         */
        @Override
        public long getCount(double value) {
            // The fork node recursively calls the appropriate subtree depending on the split value.
            long count;
            if (value > splitValue) {
                count = right.getCount(value);
            } else {
                count = left.getCount(value);
            }
            return count;
        }

        /**
         * Returns the cumulative density function for a given data point.
         * @param value the reference data point.
         * @return the cumulative density function for the reference point.
         */
        @Override
        public long getAccumCount(double value) {
            // The fork node recursively calls the appropriate subtree depending on the split value.
            long count = left.getAccumCount(value);
            if (value > splitValue) {
                count += right.getAccumCount(value);
            }
            return count;
        }

        /**
         * Returns the data point where the running cumulative count reaches the target cumulative count.
         * @param accumCount
         *		accumCount[0] the running cumulative count.
         *		accumCount[1] the target cumulative count.
         * @return the data point where the running cumulative count reaches the target cumulative count.
         */
        @Override
        public Double getValueForAccumCount(long[] accumCount) {
            Double val = left.getValueForAccumCount(accumCount);
            if (val == null)
                val = right.getValueForAccumCount(accumCount);

            return val;
        }

        /**
         * Applies a conversion function to the values stored in the histogram.
         *
         * @param valueConversion a class that defines a function to convert the value
         */
        @Override
        public void apply(AdaptiveHistogram.ValueConversion valueConversion) {
            left.apply(valueConversion);
            right.apply(valueConversion);
            splitValue = valueConversion.convertValue(splitValue);
        }


        /**
         * Prints the data for the nodes in its subtrees.
         *
         * @param level the level of this node in the tree
         */
        @Override
        public void show(int level) {
            left.show(level+1);
            margin(level);
            System.out.println("Fork at: " + splitValue);
            right.show(level+1);
        }
        /**
         * Build the table representing the histogram data adding the data from each subtree.
         */
        @Override
        public void toTable(List<Cell> table) {
            left.toTable(table);
            right.toTable(table);
        }
    }

    /**
     * Contains the data of a histogram node.
     */
    public static class Cell implements Serializable {
        public long count;
        public double minValue, maxValue;
    }
}
