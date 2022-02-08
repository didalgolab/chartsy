package one.chartsy.data;

public interface ChronologicalDataset {

    int length();

    long getTimeAt(int index);
}
