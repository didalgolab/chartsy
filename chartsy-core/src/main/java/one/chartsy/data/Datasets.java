package one.chartsy.data;

public class Datasets {

    public static int requireValidIndex(int index, SequenceAlike<?,?> dataset) {
        if (dataset.isUndefined(index))
            throw new IndexOutOfBoundsException(String.valueOf(index));
        return index;
    }

}
