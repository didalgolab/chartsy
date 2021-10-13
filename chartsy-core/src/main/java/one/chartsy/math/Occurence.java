package one.chartsy.math;

import java.io.Serial;
import java.io.Serializable;

public final class Occurence implements Serializable {
    @Serial
    private static final long serialVersionUID = -7280158659026064910L;

    private int id;
    private int count;
    private double total;
    private double average;
    private double maximum;
    private double minimum;
    private double positiveTotal;
    private double negativeTotal;
    private int positiveCount;
    private int negativeCount;
    
    public static Occurence[] newArray(int size) {
        Occurence[] array = new Occurence[size];
        for (int i = 0; i < size; i++)
            array[i] = new Occurence();
        return array;
    }
    
    public enum Type {
        COUNT,
        TOTAL,
        AVERAGE,
        MAXIMUM,
        MINIMUM,
        POSITIVE_TOTAL,
        NEGATIVE_TOTAL,
        POSITIVE_COUNT,
        NEGATIVE_COUNT;
    }
    
    public double get(Type type) {
        return switch (type) {
            case COUNT -> count;
            case TOTAL -> total;
            case AVERAGE -> average;
            case MAXIMUM -> maximum;
            case MINIMUM -> minimum;
            case POSITIVE_TOTAL -> positiveTotal;
            case NEGATIVE_TOTAL -> negativeTotal;
            case POSITIVE_COUNT -> positiveCount;
            case NEGATIVE_COUNT -> negativeCount;
        };
    }
    
    @Override
    public String toString() {
        return "Aggregator[id=" + id + "]{\n" +
                "  average=" + average + ",\n" +
                "  maximum=" + maximum + ",\n" +
                "  minimum=" + minimum + ",\n" +
                "  positiveTotal=" + positiveTotal+",\n" +
                "  negativeTotal=" + negativeTotal+",\n" +
                "  positiveCount=" + positiveCount+",\n" +
                "  negativeCount=" + negativeCount+",\n" +
                "  count: " + count +
                "\n}";
    }
    
    public Occurence() {
    }
    
    public Occurence(int id) {
        this.id = id;
    }
    
    public void add(double val) {
        if (val > maximum || count == 0)
            maximum = val;
        if (val < minimum || count == 0)
            minimum = val;
        if (val > 0) {
            positiveCount++;
            positiveTotal += val;
        }
        if (val < 0) {
            negativeCount++;
            negativeTotal -= val;
        }
        average = (total += val)/(++count);
    }
    
    public int getId() {
        return id;
    }
    
    public double getMaximum() {
        return maximum;
    }
    
    public double getMinimum() {
        return minimum;
    }
    
    public double getAverage() {
        return average;
    }
    
    public double getTotal() {
        return total;
    }
    
    public int getCount() {
        return count;
    }
    
    public double getPositiveTotal() {
        return positiveTotal;
    }
    
    public int getNegativeCount() {
        return negativeCount;
    }
    
    public double getNegativeTotal() {
        return negativeTotal;
    }
    
    public int getPositiveCount() {
        return positiveCount;
    }
}
