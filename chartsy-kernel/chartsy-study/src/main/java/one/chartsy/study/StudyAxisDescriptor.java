package one.chartsy.study;

public record StudyAxisDescriptor(
        double min,
        double max,
        boolean logarithmic,
        boolean includeInRange,
        double[] steps
) {
    public StudyAxisDescriptor {
        steps = steps == null ? new double[0] : steps.clone();
    }

    public StudyAxisDescriptor() {
        this(Double.NaN, Double.NaN, false, true, new double[0]);
    }

    public boolean hasFixedMin() {
        return !Double.isNaN(min);
    }

    public boolean hasFixedMax() {
        return !Double.isNaN(max);
    }

    @Override
    public double[] steps() {
        return steps.clone();
    }
}
