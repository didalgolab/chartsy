package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.trade.algorithm.MarketSupplier;
import one.chartsy.util.CloseHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * A {@code MarketSupplierFactory} decorator that emits a single-symbol stream of {@code TradeBar}s
 * whose timestamps match the original stream but whose bar returns are bootstrapped
 * (with replacement) from the original series, destroying serial dependence while
 * retaining the empirical distribution of bar-to-bar shapes. The path is anchored
 * to the first bar's open price.
 *
 * <p>Reusability and randomness:
 * <ul>
 *   <li>Each created supplier is reusable across multiple open/close cycles; every cycle
 *       replays the same precomputed synthetic path.</li>
 *   <li>Using the no-seed constructor produces a different synthetic path for each {@link #create()}.</li>
 *   <li>Using the seeded constructor yields reproducible paths.</li>
 * </ul>
 */
public final class BootstrappedTradeBarFactory implements MarketSupplierFactory {

    private final MarketSupplierFactory delegate;
    private final SplittableRandom seedRandom;
    private volatile OriginalSeries originals;
    private volatile List<TradeBar> synthetic;
    
    public BootstrappedTradeBarFactory(MarketSupplierFactory delegate) {
        this(delegate, null);
    }

    public BootstrappedTradeBarFactory(MarketSupplierFactory delegate, long seed) {
        this(delegate, new SplittableRandom(seed));
    }

    private BootstrappedTradeBarFactory(MarketSupplierFactory delegate, SplittableRandom seedRandom) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.seedRandom = seedRandom;
    }

    @Override
    public MarketSupplier create() {
        var source = delegate.create();
        var rng = (seedRandom != null) ? seedRandom.split() : new SplittableRandom();
        if (originals == null) {
            synchronized (this) {
                if (originals == null)
                    originals = OriginalSeries.createFrom(source);
            }
        }
        
        var syntheticSeries = createSyntheticSeries(originals, rng);
        if (seedRandom != null && synthetic == null) {
            synchronized (seedRandom) {
                if (synthetic == null)
                    synthetic = syntheticSeries;
            }
        }
        return new BootstrappedSupplier(syntheticSeries);
    }

    private static List<TradeBar> createSyntheticSeries(OriginalSeries originals, SplittableRandom rng) {
        return createSyntheticSeries(originals.symbol, originals.originals, originals.factors, rng);
    }

    private static final class BootstrappedSupplier implements MarketSupplier {
        private final List<TradeBar> synthetic;
        private boolean open;
        private int index;

        BootstrappedSupplier(List<TradeBar> synthetic) {
            this.synthetic = synthetic;
        }

        @Override
        public void open() {
            if (open)
                throw new IllegalStateException("Already open");
            index = 0;
            open = true;
        }

        @Override
        public int poll(MarketMessageHandler handler, int pollLimit) {
            if (!open)
                throw new IllegalStateException("Supplier not open");
            if (index >= synthetic.size())
                return 0;

            int limit = Math.max(1, pollLimit);
            long t = synthetic.get(index).getTime();
            int delivered = 0;
            while (index < synthetic.size()
                    && synthetic.get(index).getTime() == t
                    && delivered < limit) {
                handler.onMarketMessage(synthetic.get(index++));
                delivered++;
            }
            return delivered;
        }

        @Override
        public void close() {
            open = false;
        }
    }

    private static List<TradeBar> createSyntheticSeries(
            SymbolIdentity symbol,
            List<Candle> originals,
            List<Candle> bag,
            RandomGenerator rng) {

        List<TradeBar> out = new ArrayList<>(originals.size());
        if (originals.isEmpty())
            return out;

        if (bag.isEmpty()) {
            for (Candle c : originals)
                out.add(new TradeBar.Of(symbol, c));
            return out;
        }

        double anchorOpen = originals.getFirst().open();
        Candle f = pickFactorWithNonZeroOpen(bag, rng);
        if (f == null) {
            for (Candle c : originals)
                out.add(new TradeBar.Of(symbol, c));
            return out;
        }

        double prevClose = anchorOpen / f.open();

        for (int i = 0; i < originals.size(); i++) {
            if (i > 0)
                f = pickUsableFactor(bag, rng);
            long time = originals.get(i).getTime();

            double open = prevClose * f.open();
            double close = prevClose * f.close();
            double high = Math.max(Math.max(open, close), prevClose * f.high());
            double low  = Math.min(Math.min(open, close), prevClose * f.low());

            double volume = f.volume();

            Candle c = Candle.of(time, open, high, low, close, volume);
            out.add(new TradeBar.Of(symbol, c));

            prevClose = close;
        }
        return out;
    }

    private static Candle pickFactorWithNonZeroOpen(List<Candle> bag, RandomGenerator rng) {
        int tries = Math.max(8, bag.size() * 2);
        Candle best = null;
        for (int i = 0; i < tries; i++) {
            Candle f = bag.get(rng.nextInt(bag.size()));
            if (isFinitePositive(f.open()) && isFinitePositive(f.close())
                    && isFinitePositive(f.high()) && isFinitePositive(f.low())) {
                return f;
            }
            if (best == null && isFinite(f.open()))
                best = f;
        }
        return best;
    }

    private static Candle pickUsableFactor(List<Candle> bag, RandomGenerator rng) {
        int tries = Math.max(8, bag.size() * 2);
        Candle last = null;
        for (int i = 0; i < tries; i++) {
            Candle f = bag.get(rng.nextInt(bag.size()));
            last = f;
            if (isFinitePositive(f.open()) && isFinitePositive(f.close())
                    && isFinitePositive(f.high()) && isFinitePositive(f.low())) {
                return f;
            }
        }
        return last;
    }

    private static boolean isFinite(double x) {
        return Double.isFinite(x);
    }

    private static boolean isFinitePositive(double x) {
        return Double.isFinite(x) && x > 0.0;
    }

    private static final class OriginalSeries implements MarketMessageHandler {
        private SymbolIdentity symbol;
        private final List<Candle> originals = new ArrayList<>();
        private final List<Candle> factors = new ArrayList<>();
        private Candle last;

        @Override
        public void onMarketMessage(MarketEvent event) {
            if (!(event instanceof TradeBar tb))
                throw new IllegalStateException("Only TradeBar events are supported: " + event.getClass().getName());

            Candle curr = tb.get();
            if (last != null) {
                double base = last.close();
                if (Double.isFinite(base) && base != 0.0) {
                    double oF = curr.open() / base;
                    double cF = curr.close() / base;
                    double hF = curr.high() / base;
                    double lF = curr.low() / base;
                    double hi = Math.max(hF, Math.max(oF, cF));
                    double lo = Math.min(lF, Math.min(oF, cF));
                    factors.add(Candle.of(0L, oF, hi, lo, cF, curr.volume()));
                }
            }
            originals.add(curr);
            last = curr;
        }
        
        private static OriginalSeries createFrom(MarketSupplier source) {
            OriginalSeries collector = new OriginalSeries();
            source.open();
            try {
                while (true)
                    if (source.poll(collector, Integer.MAX_VALUE) <= 0)
                        break;
            } finally {
                CloseHelper.closeQuietly(source);
            }
            return collector;
        }
    }
}
