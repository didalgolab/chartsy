package one.chartsy.trade;

import java.io.Serializable;

/** Protection committed with an entry. One holding bar means the entry bar's close. */
public record ExitRules(double stopDistance, int closeAfterBars) implements Serializable {
    public ExitRules {
        if (!Double.isFinite(stopDistance) || stopDistance <= 0)
            throw new IllegalArgumentException("stopDistance must be positive and finite");
        if (closeAfterBars <= 0)
            throw new IllegalArgumentException("closeAfterBars must be positive");
    }
}
