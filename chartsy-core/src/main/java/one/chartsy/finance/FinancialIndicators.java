package one.chartsy.finance;

import one.chartsy.core.collections.DoubleMinMaxList;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.data.packed.PackedDoubleSeries;
import one.chartsy.data.packed.PackedCandleSeries;

import java.util.ArrayList;
import java.util.List;

public class FinancialIndicators {

    public static List<DoubleSeries> sfora(PackedCandleSeries series) {
        return sfora(series, new Sfora.Properties());
    }

    public static List<DoubleSeries> sfora(PackedCandleSeries series, Sfora.Properties props) {
        List<DoubleSeries> resultList = new ArrayList<>();
        Sfora.calculate(series, props, resultList);
        return resultList;
    }

    public static PackedDoubleSeries leadingFrama(CandleSeries quotes, int framaPeriods) {
        var frama = frama(quotes, framaPeriods);
        var atr = quotes.atr(15);
        return frama.add(atr);
    }

    public static PackedDoubleSeries frama(CandleSeries quotes, int periods) {
        return frama(quotes, periods, quotes.length() - 1, Double.NaN);
    }

    public static PackedDoubleSeries frama(CandleSeries quotes, int periods, int firstBarNo, double firstValue) {
        final double compliance = 5.0;
        final int k = 3;
        final double alpha = 2.0/(1.0 + k*periods);

        DoubleSeries closes = quotes.closes();
        DoubleSeries fdi = fastFdi(closes, periods);
        if (fdi.length() == 0)
            return DoubleSeries.empty(quotes.getTimeline());

        int length = Math.min(fdi.length(), firstBarNo + 1);
        double[] z = new double[length];
        double previousValue = closes.get(z.length - 1);
        if (firstValue == firstValue)
            previousValue = firstValue;
        for (int i = z.length - 1; i >= 0; i--) {
            double weight = fdi.get(i);
            if (weight > 1.7)
                weight = 1.7;
            else if (weight < 1.3)
                weight = 1.3;

            double coeff = alpha * Math.exp(-compliance*(weight - 1.5));
            z[i] = previousValue = (closes.get(i) - previousValue)*coeff + previousValue;
        }

        return DoubleSeries.of(z, quotes.getTimeline());
    }

    public static DoubleSeries fastFdi(DoubleSeries x, int periods) {
        int count = x.length();
        if (count < periods)
            return DoubleSeries.empty(x.getTimeline());

        double currentValue = 1.5;
        double[] lengths = new double[periods - 1];
        int index = 0;
        double[] z = new double[count - periods + 1];
        double length = 0;
        for (int lookback = 2; lookback < periods; lookback++) {
            double diff = (x.get(z.length - 1 + lookback) - x.get(z.length - 1 + lookback - 1));
            length += lengths[++index] = Math.abs(diff);
        }
        // TODO
        double[] data = x.values().toArray();
        double[] max = MathHelper.runmax(data, periods);
        double[] min = MathHelper.runmin(data, periods);
        final double log2 = Math.log(2);
        final double sqrt2m1 = Math.sqrt(2) - 1;
        final double denominator = Math.log(2 * (periods - 1));
        for (int barNo = z.length - 1; barNo >= 0; barNo--) {
            double priceMax = max[barNo];
            double priceMin = min[barNo];

            length -= lengths[index = (index + 1) % lengths.length];
            double diff = (x.get(barNo + 1) - x.get(barNo));
            length += lengths[index] = Math.abs(diff);

            if (priceMax - priceMin > 0.0)
                currentValue = 1.0 + (Math.log(length / (priceMax - priceMin) + sqrt2m1) + log2) / denominator;

            z[barNo] = currentValue;
        }
        return DoubleSeries.of(z, x.getTimeline());
    }


    public static final class Sfora {

        public static record Properties(int framaPeriod, int slowdownPeriod, int numberOfEnvelops) {
            private static final int DEFAULT_FRAMA_PERIOD = 45;
            private static final int DEFAULT_SLOWDOWN_PERIOD = 16;
            private static final int DEFAULT_NUMBER_OF_ENVELOPS = 8;

            public Properties() {
                this(DEFAULT_FRAMA_PERIOD, DEFAULT_SLOWDOWN_PERIOD, DEFAULT_NUMBER_OF_ENVELOPS);
            }
        }

        public static DoubleMinMaxList bands(PackedCandleSeries series) {
            return bands(series, new Properties());
        }

        public static DoubleMinMaxList bands(CandleSeries series, Properties props) {
            DoubleMinMaxList resultList = new DoubleMinMaxList();
            calculate(series, props, resultList);
            return resultList;
        }

        public static void calculate(CandleSeries series, Properties props, List<DoubleSeries> resultList) {
            for (int i = 1; i <= props.numberOfEnvelops; i++) {
                var frama = leadingFrama(series, props.framaPeriod + 1 - i);
                resultList.add(frama.sma(i * props.slowdownPeriod));
            }
        }
    }
}
