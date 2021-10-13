package one.chartsy.trade;

import lombok.Getter;
import lombok.Setter;
import one.chartsy.SymbolIdentity;
import one.chartsy.time.Chronological;

@Getter
@Setter
public class Execution implements Chronological {
    /** The traded symbol. */
    private final SymbolIdentity symbol;
    /** The execution's identifier. */
    private final String id;
    /** The execution's time. */
    private final long time;
    /** Specifies if the transaction was buy or sale. */
    private final Direction side;
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


    public Execution(SymbolIdentity symbol, String id, long time, Direction side, double price, double size) {
        this.symbol = symbol;
        this.id = id;
        this.time = time;
        this.side = side;
        this.price = price;
        this.size = size;
    }
}
