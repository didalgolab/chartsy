package one.chartsy.simulation.reporting;

import lombok.Getter;
import lombok.experimental.Accessors;
import one.chartsy.trade.Account;
import one.chartsy.trade.data.Position;
import one.chartsy.trade.event.PositionValueChangeListener;

@Accessors(fluent = true)
@Getter
public class EquityInformation {

    private final double totalEquityHigh;
    private final double totalEquityLow;
    private final double maxDrawDown;
    private final long totalEquityHighTime;
    private final long maxDrawDownTime;

    public static class Builder implements PositionValueChangeListener {
        private double totalEquityHigh;
        private double totalEquityLow;
        private double maxDrawDown;
        private long totalEquityHighTime;
        private long maxDrawDownTime;

        @Override
        public void positionValueChanged(Account account, Position position) {
            add(position.getMarketTime(), account.getEquity());
        }

        public void add(long time, double equity) {
            // recalc drawdown related statistics
            if (equity > totalEquityHigh) {
                totalEquityHigh = equity;
                totalEquityHighTime = time;
            }
            totalEquityLow = Math.min(totalEquityLow, equity);
            double drawDown = totalEquityHigh - equity;
            if (drawDown > maxDrawDown) {
                maxDrawDown = drawDown;
                maxDrawDownTime = time;
            }
        }

        public EquityInformation build() {
            return new EquityInformation(this);
        }
    }

    protected EquityInformation(Builder builder) {
        this.totalEquityHigh = builder.totalEquityHigh;
        this.totalEquityLow = builder.totalEquityLow;
        this.maxDrawDown = builder.maxDrawDown;
        this.totalEquityHighTime = builder.totalEquityHighTime;
        this.maxDrawDownTime = builder.maxDrawDownTime;
    }
}
