package one.chartsy.trade.strategy;

public enum ExitCode {
    COMPLETED,
    STOPPED,
    FAILED,
    ABORTED;

    public boolean isNormalExit() {
        return (COMPLETED == this);
    }

    public boolean isForcedExit() {
        return !isNormalExit();
    }
}
