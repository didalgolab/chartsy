package one.chartsy.data.packed;

import one.chartsy.data.DoubleSeries;
import one.chartsy.data.DoubleSeriesSupport;
import one.chartsy.data.FluentDoubleSeries;

import java.util.function.DoubleBinaryOperator;

public abstract class AbstractDoubleSeries<DS extends AbstractDoubleSeries<DS>> implements FluentDoubleSeries {

    @Override
    public DS mul(double y) {
        return mapThread(DoubleSeriesSupport::multiply, y);
    }

    @Override
    public DS div(DoubleSeries y) {
        return mapThread(DoubleSeriesSupport::divide, y);
    }

    public abstract DS mapThread(DoubleBinaryOperator f, double y);

    @Override
    public abstract DS mapThread(DoubleBinaryOperator f, DoubleSeries other);

    @Override
    public abstract DS sma(int periods);

    @Override
    public abstract DS wilders(int periods);

    @Override
    public abstract DS highestSince();
}
