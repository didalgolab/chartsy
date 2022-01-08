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

    public static DoubleSeries trailingFrama(CandleSeries quotes) {
        DoubleMinMaxList bands = Frama.calculateSmudgeBands(quotes);
        return bands.getMaximum().add(bands.getMinimum()).mul(0.5);
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

    public static DoubleSeries frama(CandleSeries quotes, int periods, double maPeriods) {
        return frama(quotes, periods, quotes.length() - 1, Double.NaN, maPeriods);
    }

    public static DoubleSeries frama(CandleSeries quotes, int periods, int firstBarNo, double firstValue, double maPeriods) {
        return frama(quotes, periods, firstBarNo, firstValue, maPeriods, 9.0);
    }

    public static DoubleSeries frama(CandleSeries quotes, int periods, int firstBarNo, double firstValue, double maPeriods, double compliance) {
        //final double compliance = 9.0;//10.0;//5.0;
        //final int k = 3;//3;
        final double alpha = 2.0/(1.0 + maPeriods);

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
            if (weight > 1.60)
                weight = 1.60;
            //else
            if (weight < 1.40)
                weight = 1.40;

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

    public static DoubleSeries fdi(DoubleSeries series, int periods) {
        if (periods <= 0)
            throw new IllegalArgumentException("The periods argument " + periods + " must be positive");
        int newLength = series.length() - periods + 1;
        if (newLength <= 0)
            return DoubleSeries.empty(series.getTimeline());

        DoubleSeries hhv = series.hhv(periods);
        DoubleSeries llv = series.llv(periods);
        double[] result = new double[newLength];
        double value = 1.5;
        for (int barNo = 0; barNo < result.length; barNo++) {
            double max = hhv.get(barNo), min = llv.get(barNo);
            double prev = 0.0;
            double length = 0.0;
            for (int k = 0; k < periods; k++) {
                if (max - min > 0.0) {
                    double diff = (series.get(barNo + k) - min) / (max - min);
                    if (k > 0)
                        length += Math.sqrt((diff - prev)*(diff - prev) + 1.0 / Math.pow(periods - 1, 2.0));
                    prev = diff;
                }
            }
            if (length > 0.0)
                value = 1.0 + (Math.log(length) + Math.log(2)) / Math.log(2 * (periods - 1));
            result[barNo] = value;
        }
        return DoubleSeries.of(result, series.getTimeline());
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

    public static final class Frama {

        public static DoubleMinMaxList calculateSmudgeBands(CandleSeries quotes) {
            DoubleMinMaxList result = new DoubleMinMaxList();
            calculateSmudges(quotes, result);
            return result;
        }

        public static List<DoubleSeries> calculateSmudges(CandleSeries quotes) {
            List<DoubleSeries> result = new ArrayList<>();
            calculateSmudges(quotes, result);
            return result;
        }

        private static void calculateSmudges(CandleSeries quotes, List<DoubleSeries> resultList) {
            for (int i = 0; i < 5; i++) {
                //int periods = 7;

                int periods1 = 21*3 + 15*i;//FIXME: MB: CHANGED: 10*i;
                DoubleSeries frama = frama(quotes, 13/*was: 15*/, periods1);
                //int periods2 = 37*3 + 45*i;//FIXME: MB: CHANGED: 10*i;
                //Series frama2 = frama(quotes, 30/*was: 13*/, periods2);
                //Series frama2 = frama(quotes, 13);
                //Series frama3 = frama(quotes, 45);
                //Series frama4 = frama(quotes, 7);
                //Series frama2 = frama(quotes, 15);
                //Series frama = frama1.add(frama2).mul(0.5);
                for (int ref = 15; ref <= 40; ref += 5) {
                    DoubleSeries refSmudge = frama.ref(-ref);
                    //Series refSmudge2 = frama2.ref(-ref);
                    //Series refSmudge3 = frama3.ref(-ref);
                    //Series refSmudge4 = frama4.ref(-ref);
                    resultList.add(refSmudge);
                    //resultList.add(refSmudge2);
                    //resultList.add(refSmudge3);
                    //resultList.add(refSmudge4);
                    //if (true)
                    //    break;
                }
            }
        }
    }
}
