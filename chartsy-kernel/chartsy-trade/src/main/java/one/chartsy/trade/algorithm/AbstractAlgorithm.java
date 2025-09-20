/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.common.ShutdownRequest;
import one.chartsy.time.Chronological;
import one.chartsy.time.Clock;
import one.chartsy.time.DateChangeSignal;
import one.chartsy.trade.Order;
import one.chartsy.trade.OrderType;
import one.chartsy.trade.algorithm.data.DefaultInstrumentRanker;
import one.chartsy.trade.algorithm.data.DefaultMarketDataProcessor;
import one.chartsy.trade.algorithm.data.InstrumentData;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;
import one.chartsy.trade.algorithm.data.InstrumentRanker;
import one.chartsy.trade.algorithm.data.MarketDataProcessor;
import one.chartsy.trade.algorithm.data.RankingPostProcessor;
import one.chartsy.trade.algorithm.data.RankingStrategy;
import one.chartsy.trade.algorithm.data.RankingStrategy.RankingOrder;
import one.chartsy.trade.algorithm.order.AbstractOrderProcessor;
import one.chartsy.util.SequenceGenerator;
import org.springframework.beans.PropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractAlgorithm<I extends InstrumentData> implements Algorithm {

    protected final AlgorithmContext context;
    protected final String id;
    protected final MarketDataProcessor<I> marketDataProcessor;
    protected final OutboundOrderProcessor outboundOrderProcessor;
    protected final List<InstrumentRanker<I>> rankers = new ArrayList<>();
    private PropertyAccessor propertyAccessor;
    private final DateChangeSignal dateChange = DateChangeSignal.create();


    public AbstractAlgorithm(AlgorithmContext context) {
        this.context = context;
        this.id = context.getId();
        this.marketDataProcessor = createMarketDataProcessor();
        this.outboundOrderProcessor = createOutboundOrderProcessor();
    }

    public final String getId() {
        return id;
    }

    public final Clock getClock() {
        return context.getClock();
    }

    protected PropertyAccessor getPropertyAccessor() {
        if (propertyAccessor == null) {
            propertyAccessor = PropertyAccessorFactory.forDirectFieldAccess(this);
        }
        return propertyAccessor;
    }

    @Override
    public void open() {
        // nothing to do here
    }

    /**
     * Provides an {@link InstrumentDataFactory} implementation specific to the concrete algorithm,
     * responsible for instantiating instrument-specific state data objects. The factory is used
     * internally by the {@link MarketDataProcessor} to create instrument-specific state objects.
     *
     * @return the factory instance capable of creating new {@code InstrumentData} instances
     */
    protected abstract InstrumentDataFactory<I> createInstrumentDataFactory();

    protected MarketDataProcessor<I> createMarketDataProcessor() {
        return new DefaultMarketDataProcessor<>(createInstrumentDataFactory());
    }

    protected OutboundOrderProcessor createOutboundOrderProcessor() {
        return new OutboundOrderProcessor();
    }

    /**
     * Creates an instrument ranking using the given strategy, defaulting to {@code HIGHER_BETTER} ranking order.
     *
     * @param strategy the {@code RankingStrategy} to compute scores
     * @return the newly created and registered instrument ranker
     */
    protected InstrumentRanker<I> createRanking(RankingStrategy<I> strategy) {
        return createRanking(strategy, RankingPostProcessor.noop(), RankingOrder.HIGHER_BETTER);
    }

    /**
     * Creates an instrument ranking using the given strategy and rank post-processor,
     * defaulting to {@code HIGHER_BETTER} ranking order.
     *
     * @param strategy      the {@code RankingStrategy} to compute scores
     * @param postProcessor the {@code RankingPostProcessor} to assign rank numbers
     * @return the newly created and registered instrument ranker
     */
    protected InstrumentRanker<I> createRanking(RankingStrategy<I> strategy, RankingPostProcessor<I> postProcessor) {
        return createRanking(strategy, postProcessor, RankingOrder.HIGHER_BETTER);
    }

    /**
     * Creates am instrument ranking using the given strategy, rank post-processor, and the ranking order.
     *
     * @param strategy      the {@code RankingStrategy} to compute scores
     * @param postProcessor the {@code RankingPostProcessor} to assign rank numbers
     * @param order         the {@code RankingOrder} (e.g., HIGHER_BETTER or LOWER_BETTER)
     * @return the newly created and registered instrument ranker
     */
    protected InstrumentRanker<I> createRanking(RankingStrategy<I> strategy,
                                                RankingPostProcessor<I> postProcessor,
                                                RankingOrder order) {
        var ranker = new DefaultInstrumentRanker<>(this, marketDataProcessor, strategy, postProcessor, order);
        this.rankers.add(ranker);
        return ranker;
    }

    protected void onDateChange(Chronological event) {
        // nothing to do here
    }

    @Override
    public void onMarketMessage(MarketEvent event) {
        if (dateChange.poll(event))
            onDateChange(event);

        if (!rankers.isEmpty())
            for (var ranker : rankers)
                ranker.onMarketMessage(event);

        marketDataProcessor.onMarketMessage(event);
    }

    /**
     * Unregisters an instrument ranker so that it will no longer receive market messages.
     *
     * @param ranker the instrument ranker to remove from the ranking list
     */
    public void unregisterRanking(InstrumentRanker<?> ranker) {
        this.rankers.remove(ranker);
    }

    @Override
    public void close() {
        // nothing to do here
    }

    protected void validateOrderRequest(Order.New order) {
        // TODO
    }

    protected void submitOrder(Order.New order) {
        validateOrderRequest(order);
        context.getOrderHandler().onOrderPlacement(order);
    }

    @Override
    public void onOrderPlacement(Order.New order) {
        // TODO
    }

    @Override
    public void onOrderRejected(Order.Rejected rejection) {
        // TODO
    }

    @Override
    public void onOrderStatusChanged(Order.StatusChanged change) {
        // TODO
    }

    @Override
    public void onOrderFilled(Order.Filled fill) {
        // TODO
    }

    @Override
    public void onOrderPartiallyFilled(Order.PartiallyFilled partialFill) {
        // TODO
    }

    protected String generateOrderId() {
        return context.getSequenceGenerator().next();
    }

    @Override
    public void onShutdownRequest(ShutdownRequest request) {
        context.getShutdownResponseHandler().onShutdownResponse(request.toShutdownResponse(getId()));
    }

    protected Order.Builder makeMarketOrder(Order.Side side, double quantity, SymbolIdentity symbol) {
        Order.Builder order = outboundOrderProcessor.makeOrder(side, quantity, symbol);
        order.type(OrderType.MARKET);

        return order;
    }

    protected class OutboundOrderProcessor extends AbstractOrderProcessor {

        private final SequenceGenerator orderIdGenerator = AbstractAlgorithm.this::generateOrderId;

        protected Order.Builder makeOrder(Order.Side side, double quantity, SymbolIdentity symbol) {
            var builder = new Order.Builder(getClock(), orderIdGenerator);
            builder.sourceId(getId());
            builder.side(side);
            builder.quantity(quantity);
            builder.symbol(symbol);

            return builder;
        }
    }
}
