package one.chartsy.trade;

public abstract class OrderStatusUpdater {

    protected void toSubmitted(Order order, int orderId, long time) {
        fireOrderStatusChanged(order.toSubmitted(orderId, time));
    }

    protected void toCancelled(Order order, long time) {
        fireOrderStatusChanged(order.toCancelled(time));
    }

    protected void toRejected(Order order) {
        fireOrderStatusChanged(order.toRejected());
    }

    protected void toExpired(Order order) {
        fireOrderStatusChanged(order.toExpired());
    }

    protected void setAcceptedTime(Order order, long time) {
        order.setAcceptedTime(time);
    }

    protected abstract void fireOrderStatusChanged(Order order);

}
