package one.chartsy.data;

public interface DatasetListener<E> {

    void onLastValueChange(Dataset<E> source);

    void onLastValueAppend(Dataset<E> source);
}
