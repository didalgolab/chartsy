package one.chartsy.trade.strategy.annotation;

public enum IncompatibilityType {

    /**
     * The feature is only available during a simulation.
     */
    BACKTEST_ONLY,

    /**
     * The feature is only available during live trading.
     */
    LIVE_ONLY,

    /**
     * The feature exhibits a difference in behaviour between a simulation and live trading.
     */
    DIFFERENT_BEHAVIOUR,

    /**
     * The feature has limited support in live trading compared to simulation.
     */
    LIMITED_LIVE
}
