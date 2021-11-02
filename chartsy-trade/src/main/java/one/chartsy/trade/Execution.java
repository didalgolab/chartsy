package one.chartsy.trade;

import lombok.Getter;
import lombok.Setter;
import one.chartsy.SymbolIdentity;
import one.chartsy.time.Chronological;

@Getter
@Setter
public class Execution implements Chronological {
    /** The order associated with this execution. */
    private final one.chartsy.trade.Order order;
    /** The traded symbol. */
    private final SymbolIdentity symbol;
    /** Specifies if the transaction was buy or sale. */
    private final Direction side;
    /** The execution's identifier. */
    private final String id;
    /** The execution's time. */
    private final long time;
    /** The order's execution price. */
    private final double price;
    /** The order's execution quantity. */
    private final double size;
    /** The exchange where the execution took place. */
    private String exchangeName;
    /** Identifies whether an execution occurred because of a position liquidation. */
    private boolean liquidation;
    /** Indicates whether an execution occurred because of a stop loss hit. */
    private boolean stopLossHit;
    /** Indicates whether an execution occurred because of a profit target hit. */
    private boolean profitTargetHit;
    /** Indicates whether an execution is of a scale-in. */
    private boolean scaleIn;
    /** Indicates whether an execution is of a scale-out. */
    private boolean scaleOut;

    private double openingCommission;

    private double closingCommission;


    public Execution(one.chartsy.trade.Order order, String executionId, long time, double price, double size) {
        this.order = order;
        this.symbol = order.getSymbol();
        this.side = order.getSide().getDirection();
        this.id = executionId;
        this.time = time;
        this.price = price;
        this.size = size;
    }
}
