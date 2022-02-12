package one.chartsy.trade.event;

import one.chartsy.misc.Ephemeral;
import one.chartsy.trade.Account;
import one.chartsy.trade.data.Position;

public class PositionValueChangeEvent implements Ephemeral {
    private final Account account;
    protected Position position;

    public PositionValueChangeEvent(Account account) {
        this.account = account;
    }

    public void setState(Position position) {
        this.position = position;
    }

    public final Account getAccount() {
        return account;
    }

    public Position getPosition() {
        return position;
    }

    public long getTime() {
        return position.getCurrTime();
    }
}
