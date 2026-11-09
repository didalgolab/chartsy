package one.chartsy.simulation.engine;

import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.data.BestBidOfferEvent;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.simulation.engine.account.SimulatorAccount;
import one.chartsy.simulation.engine.price.QuoteSnapshot;
import one.chartsy.simulation.reporting.EquitySummaryStatistics;
import one.chartsy.trade.Order;
import one.chartsy.trade.OrderType;
import one.chartsy.trade.PositionClose;
import one.chartsy.trade.PositionMaintenance;
import one.chartsy.trade.PositionRequest;
import one.chartsy.trade.PositionStopUpdate;
import one.chartsy.trade.PositionTarget;
import one.chartsy.trade.TimeInForce;
import one.chartsy.trade.account.AccountBalanceEntry.Position;
import one.chartsy.trade.account.ProfitConversion;
import one.chartsy.trade.data.OrderRequestEvent;
import one.chartsy.trade.service.OrderReportHandler;
import one.chartsy.trade.service.connector.AbstractTradeConnector;
import one.chartsy.trade.service.connector.TradeConnectorContext;
import one.chartsy.util.SequenceGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * Full-fill event broker with candle execution by default and optional ordered bid/ask execution.
 * Daily opening decisions own entries; precommitted stops execute on completed ranges or quotes,
 * according to the selected mode. One protected entry per account/symbol is supported.
 * All fills and executable-side valuations use the account ledger.
 */
public class TradingSimulator extends AbstractTradeConnector implements MarketMessageHandler {
    private static final String DEFAULT_ACCOUNT_ID = "";
    private final SequenceGenerator executionIds = SequenceGenerator.create();
    private final SimulatorAccount defaultAccount;
    private final EquitySummaryStatistics equityStatistics;
    private final Map<String, SimulatorAccount> subAccounts = new LinkedHashMap<>();
    private final Map<SymbolIdentity, InstrumentOrders> instrumentOrders = new LinkedHashMap<>();
    private final Set<String> orderIds = new HashSet<>();
    private final OrderReportHandler reports;
    private final BacktestExecutionOptions options;
    private long deferQuoteRequestsAfter = Long.MIN_VALUE;
    private final List<Execution> executions = new ArrayList<>();
    private final List<EquityPoint> equityCurve = new ArrayList<>();
    private final List<TargetDecision> targetDecisions = new ArrayList<>();
    private final List<MaintenanceDecision> maintenanceDecisions = new ArrayList<>();
    private final List<CloseDecision> closeDecisions = new ArrayList<>();
    private final List<StopUpdateDecision> stopUpdateDecisions = new ArrayList<>();

    /** Entry refusal code reserved for otherwise valid sizing with finite marked equity at or below zero. */
    public static final String INSUFFICIENT_EQUITY = "INSUFFICIENT_EQUITY";

    public enum Reason { ENTRY, STOP, SIGNAL_REVERSAL, SCHEDULED_CLOSE, END_OF_DATA, SIGNAL_CLOSE, TAKE_PROFIT }
    /**
     * Quote mode marks every leg at the current executable bid/ask snapshot. Candle mode uses
     * the opening quote or completed-bar close and cannot reconstruct the intrabar stop path.
     */
    public record Execution(Order.Filled fill, String parentOrderId, Reason reason,
                            double realizedPnl, double equity) { }
    public record EquityPoint(long time, double equity, double realizedPnl, double unrealizedPnl, int executionCount) { }
    public enum TargetAction { OPEN, HOLD, RESET_STOP, REVERSE, RESET_BRACKET }
    public record TargetDecision(long time, String orderId, String accountId, SymbolIdentity symbol,
                                 Order.Side side, TargetAction action, String parentOrderId, double equity,
                                 double riskFraction, double riskBudget, double quantity, double stopDistance,
                                 double stopBefore, double stopAfter, double takeProfitDistance,
                                 double takeProfitBefore, double takeProfitAfter,
                                 PositionTarget.IntrabarExitPolicy intrabarExitPolicy) {
        public TargetDecision(long time, String orderId, String accountId, SymbolIdentity symbol,
                              Order.Side side, TargetAction action, String parentOrderId, double equity,
                              double riskFraction, double riskBudget, double quantity, double stopDistance,
                              double stopBefore, double stopAfter) {
            this(time, orderId, accountId, symbol, side, action, parentOrderId, equity, riskFraction,
                    riskBudget, quantity, stopDistance, stopBefore, stopAfter, 0, Double.NaN, Double.NaN,
                    PositionTarget.IntrabarExitPolicy.STOP_FIRST);
        }
    }
    public enum MaintenanceAction { NO_POSITION, HOLD, RESET_STOP, CLOSE_REVERSAL }
    /** Equity is marked immediately before maintenance; a close retains its separate fill evidence. */
    public record MaintenanceDecision(long time, String orderId, String accountId, SymbolIdentity symbol,
                                      Order.Side signalSide, MaintenanceAction action, String parentOrderId,
                                      double equity, double quantity, double stopBefore, double stopAfter) { }
    public enum StopUpdateAction { NO_POSITION, HOLD, RESET_STOP }
    /** A stop-only request carries no signal; heldSide and parent are absent when flat. */
    public record StopUpdateDecision(long time, String orderId, String accountId, SymbolIdentity symbol,
                                     Order.Side heldSide, StopUpdateAction action, String parentOrderId,
                                     double equity, double quantity, double stopDistance, double candidateStop,
                                     double stopBefore, double stopAfter) { }
    public enum CloseAction { NO_POSITION, CLOSE }
    /** Equity and protection are recorded before the optional close fill. */
    public record CloseDecision(long time, String orderId, String accountId, SymbolIdentity symbol,
                                CloseAction action, String parentOrderId, double equity,
                                double quantity, double stopBefore) { }
    public record OpenPosition(String accountId, SymbolIdentity symbol, String parentOrderId, Order.Side side,
                               double quantity, double entryPrice, long entryTime, double stopPrice,
                               double marketPrice, long marketTime, double unrealizedPnl, double takeProfitPrice) {
        public OpenPosition(String accountId, SymbolIdentity symbol, String parentOrderId, Order.Side side,
                            double quantity, double entryPrice, long entryTime, double stopPrice,
                            double marketPrice, long marketTime, double unrealizedPnl) {
            this(accountId, symbol, parentOrderId, side, quantity, entryPrice, entryTime, stopPrice,
                    marketPrice, marketTime, unrealizedPnl, Double.NaN);
        }
    }

    public TradingSimulator(TradeConnectorContext context) {
        this(context, BacktestExecutionOptions.DEFAULT);
    }

    public TradingSimulator(TradeConnectorContext context, BacktestExecutionOptions options) {
        super(context);
        this.options = options;
        this.defaultAccount = createDefaultAccount();
        this.equityStatistics = usesSharedCapital()
                ? EquitySummaryStatistics.forSignedEquity(options.initialBalance(), SimulatorAccount.DEFAULT_ANNUAL_RISK_FREE_RATE)
                : defaultAccount.getEquityStatistics();
        this.reports = context.getOrderReportHandler();
    }

    protected SimulatorAccount createDefaultAccount() { return createAccount(DEFAULT_ACCOUNT_ID); }
    protected SimulatorAccount createAccount(String id) {
        double initial = usesSharedCapital() && !DEFAULT_ACCOUNT_ID.equals(id) ? 0 : options.initialBalance();
        return new SimulatorAccount(id, initial, options.profitConversion());
    }
    public final SimulatorAccount getDefaultAccount() { return defaultAccount; }
    public final SimulatorAccount getOrCreateNewAccount(String id) {
        return id == null || id.isEmpty() ? defaultAccount : subAccounts.computeIfAbsent(id, this::createAccount);
    }
    public void forEachAccount(Consumer<? super SimulatorAccount> action) {
        action.accept(defaultAccount);
        subAccounts.values().forEach(action);
    }
    /** Portfolio totals in shared mode; default-account totals in independent mode. */
    public double getTotalEquity() { return accountTotal(SimulatorAccount::getTotalEquity); }
    public double getRealizedPnL() { return accountTotal(SimulatorAccount::getRealizedPnL); }
    public double getUnrealizedPnL() { return accountTotal(SimulatorAccount::getUnrealizedPnL); }
    public double getCashBalance() { return options.initialBalance() + getRealizedPnL(); }
    public EquitySummaryStatistics getEquityStatistics() { return equityStatistics; }

    private boolean usesSharedCapital() {
        return options.capitalMode() == BacktestExecutionOptions.CapitalMode.SHARED;
    }

    private double accountTotal(ToDoubleFunction<SimulatorAccount> value) {
        double total = value.applyAsDouble(defaultAccount);
        if (usesSharedCapital())
            for (var account : subAccounts.values()) total += value.applyAsDouble(account);
        return total;
    }

    private double riskEquity(SimulatorAccount account) {
        return usesSharedCapital() ? getTotalEquity() : account.getTotalEquity();
    }

    public List<Execution> getExecutions() { return List.copyOf(executions); }
    public List<EquityPoint> getEquityCurve() { return List.copyOf(equityCurve); }
    public List<TargetDecision> getTargetDecisions() { return List.copyOf(targetDecisions); }
    public List<MaintenanceDecision> getMaintenanceDecisions() { return List.copyOf(maintenanceDecisions); }
    public List<CloseDecision> getCloseDecisions() { return List.copyOf(closeDecisions); }
    public List<StopUpdateDecision> getStopUpdateDecisions() { return List.copyOf(stopUpdateDecisions); }
    public List<OpenPosition> getOpenPositions() {
        var result = new ArrayList<OpenPosition>();
        for (var state : instrumentOrders.values()) {
            for (var entry : state.lastOrders.entrySet()) {
                var key = entry.getKey();
                var parent = entry.getValue();
                var position = getOrCreateNewAccount(key.accountId).getOrCreateNewBalance(key.currency)
                        .getPosition(parent.getSymbol());
                if (position == null) continue;
                var exit = state.exits.get(key.accountId);
                result.add(new OpenPosition(key.accountId, parent.getSymbol(), parent.getId(),
                        position.getDirection().isLong() ? Order.Side.BUY : Order.Side.SELL_SHORT,
                        position.getQuantity(), position.getAveragePrice(), position.getEntryTime(),
                        exit == null ? Double.NaN : exit.stop, position.getMarketPrice(), position.getMarketTime(),
                        position.getProfit(), exit == null ? Double.NaN : exit.takeProfit));
            }
        }
        return List.copyOf(result);
    }

    @Override public void open() { }
    @Override public void close() { }
    @Override public void onOrderPlacement(Order.New request) { enqueue(request, request.symbol(), "order"); }
    @Override public void onPositionStopUpdate(PositionStopUpdate request) { enqueue(request); }
    @Override public void onPositionClose(PositionClose request) { enqueue(request); }
    @Override public void onPositionMaintenance(PositionMaintenance request) { enqueue(request); }
    @Override public void onPositionTarget(PositionTarget target) { enqueue(target); }

    private void enqueue(PositionRequest request) {
        enqueue(request, request.symbol(), kindOf(request));
    }

    /** Requests wait for their symbol's next eligible opening; nothing executes on arrival. */
    private void enqueue(OrderRequestEvent request, SymbolIdentity symbol, String kind) {
        if (symbol == null) {
            rejectOrderRequest(currentTime(), request, "Missing " + kind + " symbol");
            return;
        }
        ordersFor(symbol).inbound.add(new PendingRequest(request, deferQuoteRequestsAfter));
    }

    /** The request's name in rejection reasons. */
    private static String kindOf(PositionRequest request) {
        return switch (request) {
            case PositionTarget _ -> "target";
            case PositionStopUpdate _ -> "stop-update";
            case PositionClose _ -> "close";
            case PositionMaintenance _ -> "maintenance";
        };
    }

    boolean usesQuoteExecution() { return options.executionMode().usesQuotes(); }

    /** Requests generated by quote/fill callbacks cannot re-enter at the same quote timestamp. */
    void collectQuoteCallbackRequests(long quoteTime, Runnable collect) {
        long previous = deferQuoteRequestsAfter;
        deferQuoteRequestsAfter = quoteTime;
        try { collect.run(); }
        finally { deferQuoteRequestsAfter = previous; }
    }

    @Override public void onMarketMessage(MarketEvent event) {
        var state = ordersFor(event.symbol());
        switch (event) {
            case BestBidOfferEvent bbo -> {
                if (usesQuoteExecution()) processQuote(state, bbo);
            }
            case BarOpen open -> {
                state.lastOpen = open;
                var quote = openingQuote(state, open);
                markAccounts(open.symbol(), quote);
                processOpeningProtections(state, open);
                matchOpeningOrders(open, 0);
            }
            case TradeBar bar -> completeBar(state, bar);
            default -> { }
        }
    }

    private void completeBar(InstrumentOrders state, TradeBar bar) {
        if (usesQuoteExecution()) {
            if (state.quote == null || state.quote.time() > bar.time())
                throw new IllegalStateException("Completed bar has no causal execution quote");
        } else {
            if (state.lastOpen == null)
                matchRawBarOrders(state, bar);
            state.quote = fixedQuote(state, bar.get().close(), bar.time());
        }
        processCompletedBar(state, bar);
        if (usesQuoteExecution()) {
            // Quotes already mark every position. Completion samples daily statistics only.
            sampleEquityStatistics(bar.time());
        } else if (usesSharedCapital()) {
            forEachAccount(account -> account.onMarketPrice(bar.symbol(), bar.get().close(), state.spread, bar.time()));
            equityStatistics.add(getTotalEquity(), bar.time());
        } else {
            forEachAccount(account -> account.onTradeBar(bar, state.spread));
        }
        equityCurve.add(equityPoint(bar.time()));
    }

    private void sampleEquityStatistics(long time) {
        if (usesSharedCapital())
            equityStatistics.add(getTotalEquity(), time);
        else
            forEachAccount(account -> account.getEquityStatistics().add(account.getTotalEquity(), time));
    }

    private void processQuote(InstrumentOrders state, BestBidOfferEvent event) {
        state.nativeQuote = state.nativeQuote.update(event.get());
        var nativeQuote = state.nativeQuote;
        if (options.executionMode() == BacktestExecutionOptions.ExecutionMode.TICK_FIXED_SPREAD) {
            if (!nativeQuote.hasBid()) return;
            state.quote = fixedQuote(state, nativeQuote.bid(), nativeQuote.time());
        } else {
            if (!nativeQuote.isComplete()) return;
            state.quote = new ExecutableQuote(nativeQuote.bid(), nativeQuote.ask(), nativeQuote.time());
        }
        markAccounts(event.symbol(), state.quote);
        for (var entry : new ArrayList<>(state.exits.entrySet())) {
            var exit = entry.getValue();
            processQuoteProtection(state, entry.getKey(), exit, event.time(), state.quote);
        }
    }

    private ExecutableQuote openingQuote(InstrumentOrders state, BarOpen open) {
        if (!usesQuoteExecution()) state.quote = fixedQuote(state, open.open(), open.time());
        else if (state.quote == null || state.quote.time() != open.time() || state.quote.bid() != open.open())
            throw new IllegalStateException("Quote execution requires the opening's actual bid/ask snapshot at the same timestamp");
        return state.quote;
    }

    private ExecutableQuote fixedQuote(InstrumentOrders state, double bid, long time) {
        return new ExecutableQuote(bid, bid + state.spread, time);
    }

    private void markAccounts(SymbolIdentity symbol, ExecutableQuote quote) {
        forEachAccount(account -> account.onMarketQuote(symbol, quote.bid(), quote.ask(), quote.time()));
    }

    /** Compatibility with unexpanded sources: preceding requests fill at the next raw bar's open. */
    private void matchRawBarOrders(InstrumentOrders state, TradeBar bar) {
        int count = state.inbound.size();
        for (int i = 0; i < count; i++) {
            var pending = state.inbound.get(i);
            var request = pending.request();
            if (pending.afterQuoteTime() >= bar.time() || request.time() >= bar.time() || isNotYetValid(request, bar.time()))
                continue;
            state.inbound.remove(i--);
            count--;
            if (request instanceof Order.New order && order.exitRules() == null)
                processOrderPlacement(order, state,
                        new BarOpen(bar.symbol(), bar.time(), bar.time(), bar.get().open()));
            else
                rejectOrderRequest(bar.time(), request, "Position targets and attached exits require causal BarOpen replay");
        }
    }

    private static boolean isNotYetValid(OrderRequestEvent request, long time) {
        return request instanceof Order.New order && order.validSinceTime() > time;
    }

    /** Snapshot boundary after reports from pre-existing orders, before the opening decision. */
    int openingDecisionStart(BarOpen open) { return ordersFor(open.symbol()).inbound.size(); }

    /** Matches only this bounded batch; requests from fill callbacks wait for another opening. */
    void matchOpeningOrders(BarOpen open, int startIndex) {
        var state = ordersFor(open.symbol());
        int end = state.inbound.size();
        for (int i = startIndex; i < end; i++) {
            var pending = state.inbound.get(i);
            var request = pending.request();
            if (pending.afterQuoteTime() >= open.time() || request.time() > open.time() || isNotYetValid(request, open.time()))
                continue;
            state.inbound.remove(i--);
            end--;
            switch (request) {
                case Order.New order -> processOrderPlacement(order, state, open);
                case PositionTarget target -> processPositionTarget(target, state, open);
                case PositionMaintenance maintenance -> processPositionMaintenance(maintenance, state, open);
                case PositionClose close -> processPositionClose(close, state, open);
                case PositionStopUpdate update -> processPositionStopUpdate(update, state, open);
                default -> throw new IllegalStateException("Unsupported request: " + request);
            }
        }
    }

    /**
     * Admits a request to its account's owned position.
     *
     * @return the owning account, or {@code null} after rejecting a malformed, duplicate or foreign request
     */
    private SimulatorAccount admit(PositionRequest request, InstrumentOrders state, long time) {
        String problem = switch (request) {
            case PositionTarget target -> validate(target);
            case PositionStopUpdate update -> validate(update);
            case PositionClose close -> validate(close);
            case PositionMaintenance maintenance -> validate(maintenance);
        };
        if (problem == null && !orderIds.add(request.orderId()))
            problem = "Duplicate " + kindOf(request) + " ID";
        if (problem != null) {
            rejectOrderRequest(time, request, problem);
            return null;
        }
        var account = getOrCreateNewAccount(request.accountId());
        problem = ownershipProblem(account, request.symbol(), request.sourceId(), state);
        if (problem != null) {
            rejectOrderRequest(time, request, problem);
            return null;
        }
        return account;
    }

    private static boolean contradictsProtection(Position position, AttachedExit protection) {
        return position.getDirection().isLong() != protection.longPosition()
                || position.getQuantity() != protection.parent.getFilledQuantity();
    }

    private void processPositionTarget(PositionTarget target, InstrumentOrders state, BarOpen open) {
        var account = admit(target, state, open.time());
        if (account == null)
            return;
        var position = account.getOrCreateNewBalance(null).getPosition(target.symbol());
        var existing = state.exits.get(account.getId());
        var quote = openingQuote(state, open);
        double proposedStop = stopPrice(target.side(), quote, target.stopDistance());
        boolean sameDirection = position != null && position.getDirection().isLong() == target.side().isBuy();
        boolean keepsExistingStop = sameDirection && target.stopUpdatePolicy() == PositionTarget.StopUpdatePolicy.KEEP;
        if (!keepsExistingStop && !isValidProtectionPrice(proposedStop)) {
            rejectOrderRequest(open.time(), target, "Stop must permit finite positive currency conversion");
            return;
        }
        double proposedTakeProfit = takeProfitPrice(target.side(), quote, target.takeProfitDistance());
        if (!keepsExistingStop && target.takeProfitDistance() > 0 && !isValidProtectionPrice(proposedTakeProfit)) {
            rejectOrderRequest(open.time(), target, "Take profit must permit finite positive currency conversion");
            return;
        }
        double stopBefore = existing == null ? Double.NaN : existing.stop;
        double takeProfitBefore = existing == null ? Double.NaN : existing.takeProfit;
        if (sameDirection) {
            var action = updateTargetProtection(target, existing, proposedStop, proposedTakeProfit);
            recordTarget(target, account, action, existing, Double.NaN, position.getQuantity(), stopBefore, takeProfitBefore);
            processOpeningProtection(state, account.getId(), existing, open);
            return;
        }
        var action = position == null ? TargetAction.OPEN : TargetAction.REVERSE;
        if (position != null)
            closeProtected(state, account.getId(), existing, open.time(),
                    quote.exitPrice(existing.longPosition()), Reason.SIGNAL_REVERSAL, quote);

        openTargetPosition(target, state, open, account, action, stopBefore, takeProfitBefore);
    }

    private TargetAction updateTargetProtection(PositionTarget target, AttachedExit protection,
                                                double proposedStop, double proposedTakeProfit) {
        var action = updateStop(protection, proposedStop, target.stopUpdatePolicy())
                ? TargetAction.RESET_STOP : TargetAction.HOLD;
        if (target.stopUpdatePolicy() == PositionTarget.StopUpdatePolicy.RESET_FROM_OPEN) {
            double takeProfitBefore = protection.takeProfit;
            protection.takeProfit = proposedTakeProfit;
            protection.intrabarExitPolicy = target.intrabarExitPolicy();
            if (!Double.isNaN(takeProfitBefore) || !Double.isNaN(proposedTakeProfit))
                action = TargetAction.RESET_BRACKET;
        }
        return action;
    }

    private boolean isValidProtectionPrice(double price) {
        return Double.isFinite(price)
                && (options.profitConversion() != ProfitConversion.BASE_CURRENCY || price > 0);
    }

    private String ownershipProblem(SimulatorAccount account, SymbolIdentity symbol, String sourceId, InstrumentOrders state) {
        for (var key : state.lastOrders.keySet())
            if (key.accountId.equals(account.getId()) && !key.currency.isEmpty()
                    && account.getOrCreateNewBalance(key.currency).hasPosition(symbol))
                return "Existing position in another currency balance is not managed by this target";
        var position = account.getOrCreateNewBalance(null).getPosition(symbol);
        var existing = state.exits.get(account.getId());
        if (position != null && (existing == null || !existing.persistent || !existing.parent.getSourceId().equals(sourceId)))
            return "Existing position is not managed by this strategy's position targets";
        if (position == null && existing != null)
            return "Another protected balance already owns this account/symbol";
        return null;
    }

    private boolean updateStop(AttachedExit protection, double candidate, PositionTarget.StopUpdatePolicy policy) {
        boolean reset = switch (policy) {
            case KEEP -> false;
            case RESET_FROM_OPEN -> true;
            case TIGHTEN_FROM_OPEN -> protection.longPosition()
                    ? candidate > protection.stop : candidate < protection.stop;
        };
        if (reset)
            protection.stop = candidate;
        return reset;
    }

    private void processPositionStopUpdate(PositionStopUpdate request, InstrumentOrders state, BarOpen open) {
        var account = admit(request, state, open.time());
        if (account == null)
            return;
        var position = account.getOrCreateNewBalance(null).getPosition(request.symbol());
        var protection = state.exits.get(account.getId());
        if (position == null) {
            stopUpdateDecisions.add(new StopUpdateDecision(open.time(), request.orderId(), account.getId(),
                    request.symbol(), null, StopUpdateAction.NO_POSITION, null, riskEquity(account), 0,
                    request.stopDistance(), Double.NaN, Double.NaN, Double.NaN));
            return;
        }
        if (contradictsProtection(position, protection)) {
            rejectOrderRequest(open.time(), request, "Position no longer matches its protection");
            return;
        }
        var side = protection.longPosition() ? Order.Side.BUY : Order.Side.SELL_SHORT;
        double distance = switch (request.distanceSource()) {
            case PROVIDED -> request.stopDistance();
            case POSITION_ENTRY -> protection.initialStopDistance;
        };
        double before = protection.stop;
        double candidate = Double.NaN;
        if (request.stopUpdatePolicy() != PositionTarget.StopUpdatePolicy.KEEP) {
            candidate = stopPrice(side, openingQuote(state, open), distance);
            if (!isValidProtectionPrice(candidate)) {
                rejectOrderRequest(open.time(), request, "Stop must permit finite positive currency conversion");
                return;
            }
        }
        var action = updateStop(protection, candidate, request.stopUpdatePolicy())
                ? StopUpdateAction.RESET_STOP : StopUpdateAction.HOLD;
        stopUpdateDecisions.add(new StopUpdateDecision(open.time(), request.orderId(), account.getId(),
                request.symbol(), side, action, protection.parent.getId(), riskEquity(account),
                position.getQuantity(), distance, candidate, before, protection.stop));
        processOpeningProtection(state, account.getId(), protection, open);
    }

    private String validate(PositionStopUpdate request) {
        if (request.orderId() == null || request.orderId().isBlank()
                || request.sourceId() == null || request.sourceId().isBlank() || request.stopUpdatePolicy() == null)
            return "Position stop update requires an ID, source and stop-update policy";
        if (!getId().equals(request.destinationId()))
            return "Position stop update requires this broker's destination";
        if (request.distanceSource() == PositionStopUpdate.DistanceSource.PROVIDED
                && (!Double.isFinite(request.stopDistance()) || request.stopDistance() <= 0))
            return "Stop-update distance must be positive and finite";
        return accountCurrencyProblem(request.accountId());
    }

    private void processPositionClose(PositionClose request, InstrumentOrders state, BarOpen open) {
        var account = admit(request, state, open.time());
        if (account == null)
            return;
        var position = account.getOrCreateNewBalance(null).getPosition(request.symbol());
        if (position == null) {
            closeDecisions.add(new CloseDecision(open.time(), request.orderId(), account.getId(), request.symbol(),
                    CloseAction.NO_POSITION, null, riskEquity(account), 0, Double.NaN));
            return;
        }
        var protection = state.exits.get(account.getId());
        if (contradictsProtection(position, protection)) {
            rejectOrderRequest(open.time(), request, "Position no longer matches its protection");
            return;
        }
        closeDecisions.add(new CloseDecision(open.time(), request.orderId(), account.getId(), request.symbol(),
                CloseAction.CLOSE, protection.parent.getId(), riskEquity(account), position.getQuantity(),
                protection.stop));
        var quote = openingQuote(state, open);
        closeProtected(state, account.getId(), protection, open.time(),
                quote.exitPrice(protection.longPosition()), Reason.SIGNAL_CLOSE, quote);
    }

    private String validate(PositionClose request) {
        if (request.orderId() == null || request.orderId().isBlank()
                || request.sourceId() == null || request.sourceId().isBlank())
            return "Position close requires an ID and source";
        if (!getId().equals(request.destinationId()))
            return "Position close requires this broker's destination";
        return accountCurrencyProblem(request.accountId());
    }

    private void processPositionMaintenance(PositionMaintenance request, InstrumentOrders state, BarOpen open) {
        var account = admit(request, state, open.time());
        if (account == null)
            return;
        var position = account.getOrCreateNewBalance(null).getPosition(request.symbol());
        var protection = state.exits.get(account.getId());
        double equity = riskEquity(account);
        if (position == null) {
            recordMaintenance(request, account, MaintenanceAction.NO_POSITION, null, equity, 0, Double.NaN, Double.NaN);
            return;
        }
        double stopBefore = protection.stop;
        double quantity = position.getQuantity();
        var quote = openingQuote(state, open);
        if (position.getDirection().isLong() != request.signalSide().isBuy()) {
            recordMaintenance(request, account, MaintenanceAction.CLOSE_REVERSAL, protection.parent.getId(),
                    equity, quantity, stopBefore, Double.NaN);
            closeProtected(state, account.getId(), protection, open.time(),
                    quote.exitPrice(protection.longPosition()), Reason.SIGNAL_REVERSAL, quote);
            return;
        }
        var action = MaintenanceAction.HOLD;
        if (request.stopUpdatePolicy() != PositionTarget.StopUpdatePolicy.KEEP) {
            double proposedStop = stopPrice(request.signalSide(), quote, request.stopDistance());
            if (!isValidProtectionPrice(proposedStop)) {
                rejectOrderRequest(open.time(), request, "Stop must permit finite positive currency conversion");
                return;
            }
            if (updateStop(protection, proposedStop, request.stopUpdatePolicy()))
                action = MaintenanceAction.RESET_STOP;
        }
        recordMaintenance(request, account, action, protection.parent.getId(), equity, quantity, stopBefore, protection.stop);
        processOpeningProtection(state, account.getId(), protection, open);
    }

    private void recordMaintenance(PositionMaintenance request, SimulatorAccount account, MaintenanceAction action,
                                   String parentId, double equity, double quantity, double stopBefore, double stopAfter) {
        maintenanceDecisions.add(new MaintenanceDecision(currentTime(), request.orderId(), account.getId(), request.symbol(),
                request.signalSide(), action, parentId, equity, quantity, stopBefore, stopAfter));
    }

    private String validate(PositionMaintenance request) {
        if (request.orderId() == null || request.orderId().isBlank() || request.sourceId() == null || request.sourceId().isBlank()
                || request.signalSide() == null || !request.signalSide().isEntry() || request.stopUpdatePolicy() == null)
            return "Maintenance requires an ID, source, entry signal side and stop-update policy";
        if (!Double.isFinite(request.stopDistance()) || request.stopDistance() <= 0)
            return "Maintenance stop distance must be positive and finite";
        return accountCurrencyProblem(request.accountId());
    }

    private void openTargetPosition(PositionTarget target, InstrumentOrders state, BarOpen open,
                                    SimulatorAccount account, TargetAction action, double stopBefore, double takeProfitBefore) {
        // The old position has settled through the ledger before the replacement risk is sized.
        // Earlier fills at this opening, including their spread costs, are already reflected.
        double equity = riskEquity(account);
        var quote = openingQuote(state, open);
        double entryPrice = quote.entryPrice(target.side());
        double proposedStop = stopPrice(target.side(), quote, target.stopDistance());
        double lossPerUnit = -options.profitConversion().toAccountCurrency(target.symbol(), -target.stopDistance(), proposedStop, open.time());
        if (Double.isFinite(equity) && equity <= 0 && Double.isFinite(lossPerUnit) && lossPerUnit > 0) {
            rejectOrderRequest(open.time(), target, INSUFFICIENT_EQUITY);
            return;
        }
        double riskBudget = equity * target.riskFraction();
        double quantity = riskBudget / lossPerUnit;
        if (!(equity > 0) || !Double.isFinite(lossPerUnit) || lossPerUnit <= 0
                || !Double.isFinite(quantity) || quantity <= 0) {
            rejectOrderRequest(open.time(), target, "Account equity cannot fund a finite positive risk-sized position");
            return;
        }
        var request = new Order.Builder().time(open.time()).orderId(target.orderId()).sourceId(target.sourceId())
                .destinationId(getId()).accountId(account.getId()).symbol(target.symbol()).type(OrderType.MARKET)
                .side(target.side()).quantity(quantity).toNewOrder();
        var order = createOrderFrom(request);
        var protection = new AttachedExit(order, entryPrice, target.stopDistance());
        protection.takeProfit = takeProfitPrice(target.side(), quote, target.takeProfitDistance());
        protection.intrabarExitPolicy = target.intrabarExitPolicy();
        state.exits.put(account.getId(), protection);
        recordTarget(target, account, action, protection, riskBudget, quantity, stopBefore, takeProfitBefore);
        execute(open.time(), order, entryPrice, Reason.ENTRY, order.getId(), quote);
        processOpeningProtection(state, account.getId(), protection, open);
    }

    private static double stopPrice(Order.Side side, ExecutableQuote quote, double distance) {
        return quote.entryPrice(side) + (side.isBuy() ? -distance : distance);
    }

    /** Measured from the same executable entry-side quote as the stop; {@code NaN} when no take profit is requested. */
    private static double takeProfitPrice(Order.Side side, ExecutableQuote quote, double distance) {
        return distance == 0 ? Double.NaN : quote.entryPrice(side) + (side.isBuy() ? distance : -distance);
    }

    private void recordTarget(PositionTarget target, SimulatorAccount account, TargetAction action,
                              AttachedExit protection, double riskBudget, double quantity, double stopBefore, double takeProfitBefore) {
        targetDecisions.add(new TargetDecision(currentTime(), target.orderId(), account.getId(), target.symbol(),
                target.side(), action, protection.parent.getId(), riskEquity(account), target.riskFraction(),
                riskBudget, quantity, target.stopDistance(), stopBefore, protection.stop, target.takeProfitDistance(),
                takeProfitBefore, protection.takeProfit, protection.intrabarExitPolicy));
    }

    private String validate(PositionTarget target) {
        if (target.orderId() == null || target.sourceId() == null || target.side() == null
                || !target.side().isEntry() || target.stopUpdatePolicy() == null)
            return "Target requires an ID, source, entry side and stop-update policy";
        if (!Double.isFinite(target.riskFraction()) || target.riskFraction() <= 0 || target.riskFraction() > 1
                || !Double.isFinite(target.stopDistance()) || target.stopDistance() <= 0)
            return "Target risk fraction must be in (0, 1] and stop distance positive and finite";
        if (!Double.isFinite(target.takeProfitDistance()) || target.takeProfitDistance() < 0
                || target.intrabarExitPolicy() == null)
            return "Take-profit distance must be finite and nonnegative, with an intrabar exit policy";
        if (target.takeProfitDistance() > 0 && target.stopUpdatePolicy() == PositionTarget.StopUpdatePolicy.TIGHTEN_FROM_OPEN)
            return "A take-profit request requires KEEP or RESET_FROM_OPEN";
        return accountCurrencyProblem(target.accountId());
    }

    private String accountCurrencyProblem(String requestedAccount) {
        String accountId = requestedAccount == null ? "" : requestedAccount;
        for (var state : instrumentOrders.values())
            for (var key : state.lastOrders.keySet())
                if (key.accountId.equals(accountId) && !key.currency.isEmpty() && !key.currency.equals("USD"))
                    return "Risk-sized position targets require an account denominated in USD";
        return null;
    }

    private void processOrderPlacement(Order.New request, InstrumentOrders state, BarOpen open) {
        String problem = validate(request);
        if (problem != null) {
            rejectOrderRequest(open.time(), request, problem);
            return;
        }
        if (!orderIds.add(request.orderId())) {
            rejectOrderRequest(open.time(), request, "Duplicate order ID");
            return;
        }
        Order order = createOrderFrom(request);
        if (order.getExpirationTime() < open.time()) {
            changeOrderStatus(open.time(), order, Order.State.EXPIRED);
            return;
        }
        var account = getOrCreateNewAccount(order.getAccountId());
        var balance = account.getOrCreateNewBalance(order.getCurrency());
        var position = balance.getPosition(open.symbol());
        if (state.exits.containsKey(account.getId()) || order.getExitRules() != null && position != null) {
            rejectOrderRequest(open.time(), request, "Overlapping protected position on this account/symbol");
            return;
        }
        if (!order.getSide().isEntry() && (position == null
                || position.getDirection().isLong() == order.getSide().isBuy()
                || order.getQuantity() > position.getQuantity())) {
            rejectOrderRequest(open.time(), request, "Exit order must reduce an existing position");
            return;
        }
        var quote = openingQuote(state, open);
        double price = quote.entryPrice(order.getSide());
        AttachedExit attached = order.getExitRules() == null ? null : new AttachedExit(order, price);
        if (attached != null)
            state.exits.put(account.getId(), attached);
        execute(open.time(), order, price, Reason.ENTRY, order.getId(), quote);
        if (attached != null)
            processOpeningProtection(state, account.getId(), attached, open);
    }

    private String validate(Order.New order) {
        if (order.orderId() == null || order.sourceId() == null || order.side() == null)
            return "Missing order ID, source or side";
        if (order.type() != OrderType.MARKET)
            return "Only market orders are supported by the bar execution broker";
        if (order.timeInForce() != null && order.timeInForce() != TimeInForce.OPEN
                && order.timeInForce() != TimeInForce.GTC && order.timeInForce() != TimeInForce.GTD)
            return "Unsupported time-in-force; market orders execute at their next eligible open";
        if (!Double.isFinite(order.quantity()) || order.quantity() <= 0
                || !Double.isFinite(order.minQuantity()) || order.minQuantity() < 0
                || order.minQuantity() > order.quantity())
            return "Invalid full-fill quantity";
        if (order.exitRules() != null && !order.side().isEntry())
            return "Attached exits require an entry side";
        return null;
    }

    private void processOpeningProtections(InstrumentOrders state, BarOpen open) {
        for (var entry : new ArrayList<>(state.exits.entrySet()))
            processOpeningProtection(state, entry.getKey(), entry.getValue(), open);
    }

    private void processOpeningProtection(InstrumentOrders state, String accountId, AttachedExit exit, BarOpen open) {
        var quote = openingQuote(state, open);
        processQuoteProtection(state, accountId, exit, open.time(), quote);
    }

    /** An observed executable quote precedes any later high/low ambiguity. Gaps fill at that quote. */
    private void processQuoteProtection(InstrumentOrders state, String accountId, AttachedExit exit,
                                        long time, ExecutableQuote quote) {
        if (quote.triggersStop(exit.longPosition(), exit.stop))
            closeProtected(state, accountId, exit, time, quote.exitPrice(exit.longPosition()), Reason.STOP, quote);
        else if (quote.triggersTakeProfit(exit.longPosition(), exit.takeProfit))
            closeProtected(state, accountId, exit, time, quote.exitPrice(exit.longPosition()), Reason.TAKE_PROFIT, quote);
    }

    private void processCompletedBar(InstrumentOrders state, TradeBar bar) {
        // Ordered quotes have already settled every barrier; only a candle's range can still touch one.
        boolean rangeExecution = !usesQuoteExecution();
        for (var entry : new ArrayList<>(state.exits.entrySet())) {
            var exit = entry.getValue();
            boolean stopTouched = rangeExecution && state.prices.stopTouched(exit.longPosition(), exit.stop, bar.get());
            boolean takeProfitTouched = rangeExecution
                    && state.prices.takeProfitTouched(exit.longPosition(), exit.takeProfit, bar.get());
            if (takeProfitTouched && (!stopTouched || exit.intrabarExitPolicy == PositionTarget.IntrabarExitPolicy.TAKE_PROFIT_FIRST)) {
                closeProtected(state, entry.getKey(), exit, bar.time(),
                        state.prices.takeProfitFill(exit.longPosition(), exit.takeProfit, bar.get().open()), Reason.TAKE_PROFIT, state.quote);
            } else if (stopTouched) {
                closeProtected(state, entry.getKey(), exit, bar.time(),
                        state.prices.stopFill(exit.longPosition(), exit.stop, bar.get().open()), Reason.STOP, state.quote);
            } else if (exit.barsLeft > 0 && --exit.barsLeft == 0) {
                closeProtected(state, entry.getKey(), exit, bar.time(),
                        state.quote.exitPrice(exit.longPosition()), Reason.SCHEDULED_CLOSE, state.quote);
            }
        }
    }

    private void closeProtected(InstrumentOrders state, String accountId, AttachedExit exit,
                                long time, double price, Reason reason, ExecutableQuote quote) {
        // Retire both rules before dispatching callbacks. A child can never open or reverse exposure.
        state.exits.remove(accountId);
        var parent = exit.parent;
        var balance = getOrCreateNewAccount(accountId).getOrCreateNewBalance(parent.getCurrency());
        var position = balance.getPosition(parent.getSymbol());
        if (position == null || position.getDirection().isLong() != exit.longPosition())
            return;
        double quantity = Math.min(parent.getFilledQuantity(), position.getQuantity());
        var child = new Order.Builder().time(time).orderId(parent.getId() + "/exit")
                .sourceId(parent.getSourceId()).destinationId(getId()).accountId(accountId)
                .symbol(parent.getSymbol()).type(OrderType.MARKET)
                .side(exit.longPosition() ? Order.Side.SELL : Order.Side.BUY_TO_COVER)
                .quantity(quantity).currency(parent.getCurrency()).referenceOrderId(parent.getId()).toNewOrder();
        execute(time, createOrderFrom(child), price, reason, parent.getId(), quote);
    }

    protected Order createOrderFrom(Order.New request) { return Order.from(request); }

    private void execute(long time, Order order, double price, Reason reason, String parentId, ExecutableQuote quote) {
        var account = getOrCreateNewAccount(order.getAccountId());
        var balance = account.getOrCreateNewBalance(order.getCurrency());
        double before = balance.getRealizedPnL();
        var fill = new Order.Filled(time, order.getId(), getId(), order.getSourceId(), executionIds.next(),
                order.getSymbol(), order.getSide(), order.getQuantity(), price, order.getQuantity(), price);
        order.setFilledQuantity(fill.cumulativeQuantity());
        order.setAverageFillPrice(fill.averagePrice());
        order.setState(Order.State.FILLED);
        balance.onOrderFill(fill);
        if (reason == Reason.ENTRY)
            ordersFor(order.getSymbol()).lastOrders.put(new BalanceKey(account.getId(),
                    order.getCurrency() == null ? "" : order.getCurrency()), order);
        if (usesSharedCapital() || usesQuoteExecution()) {
            markAccounts(order.getSymbol(), quote);
        } else {
            var position = balance.getPosition(order.getSymbol());
            if (position != null)
                balance.updateMarketData(order.getSymbol(), quote.exitPrice(position.getDirection().isLong()), time);
        }
        executions.add(new Execution(fill, parentId, reason, balance.getRealizedPnL() - before, riskEquity(account)));
        reports.onOrderFilled(fill);
    }

    protected void rejectOrderRequest(long time, OrderRequestEvent request, String reason) {
        reports.onOrderRejected(new Order.Rejected(time, request.orderId(), getId(), request.sourceId(), reason));
    }
    protected void changeOrderStatus(long time, Order order, Order.State state) {
        order.setState(state);
        reports.onOrderStatusChanged(new Order.StatusChanged(time, order.getId(), getId(), order.getSourceId(), state));
    }

    /** Finish protection and reject every request which has no remaining execution opportunity. */
    int finish() {
        int work = 0;
        for (var state : new ArrayList<>(instrumentOrders.values())) {
            work += rejectPendingRequests(state);
            if (options.endOfDataPolicy() == BacktestExecutionOptions.EndOfDataPolicy.KEEP_OPEN)
                continue;
            work += liquidateProtectedPositions(state);
            work += liquidateUnprotectedPositions(state);
        }
        recordFinalEquity();
        return work;
    }

    private int rejectPendingRequests(InstrumentOrders state) {
        int work = 0;
        var pending = new ArrayList<>(state.inbound);
        state.inbound.clear();
        for (var request : pending) {
            rejectOrderRequest(currentTime(), request.request(), "End of market data");
            work++;
        }
        return work;
    }

    private int liquidateProtectedPositions(InstrumentOrders state) {
        int work = 0;
        for (var entry : new ArrayList<>(state.exits.entrySet())) {
            var exit = entry.getValue();
            var quote = state.quote;
            closeProtected(state, entry.getKey(), exit, currentTime(),
                    quote.exitPrice(exit.longPosition()), Reason.END_OF_DATA, quote);
            work++;
        }
        return work;
    }

    private int liquidateUnprotectedPositions(InstrumentOrders state) {
        int work = 0;
        for (var entry : state.lastOrders.entrySet()) {
            var parent = entry.getValue();
            var balance = getOrCreateNewAccount(entry.getKey().accountId)
                    .getOrCreateNewBalance(entry.getKey().currency);
            var position = balance.getPosition(parent.getSymbol());
            if (position == null)
                continue;
            var quote = state.quote;
            var request = new Order.Builder().time(currentTime()).orderId(parent.getId() + "/eof")
                    .sourceId(parent.getSourceId()).destinationId(getId()).accountId(entry.getKey().accountId)
                    .symbol(parent.getSymbol()).type(OrderType.MARKET).currency(parent.getCurrency())
                    .side(position.getDirection().isLong() ? Order.Side.SELL : Order.Side.BUY_TO_COVER)
                    .quantity(position.getQuantity()).referenceOrderId(parent.getId()).toNewOrder();
            execute(currentTime(), createOrderFrom(request),
                    quote.exitPrice(position.getDirection().isLong()), Reason.END_OF_DATA, parent.getId(), quote);
            work++;
        }
        return work;
    }

    private void recordFinalEquity() {
        if (equityCurve.isEmpty() && executions.isEmpty())
            return;
        var finalPoint = equityPoint(currentTime());
        boolean sameInstant = !equityCurve.isEmpty() && equityCurve.getLast().time() == finalPoint.time();
        if (!sameInstant || equityCurve.getLast().equity() != finalPoint.equity())
            sampleEquityStatistics(finalPoint.time());
        if (sameInstant)
            equityCurve.set(equityCurve.size() - 1, finalPoint);
        else
            equityCurve.add(finalPoint);
    }

    private EquityPoint equityPoint(long time) {
        return new EquityPoint(time, getTotalEquity(), getRealizedPnL(), getUnrealizedPnL(), executions.size());
    }

    private InstrumentOrders ordersFor(SymbolIdentity symbol) {
        return instrumentOrders.computeIfAbsent(symbol, key -> new InstrumentOrders(options.spreadFor(key)));
    }
    private record PendingRequest(OrderRequestEvent request, long afterQuoteTime) { }
    private record ExecutableQuote(double bid, double ask, long time) {
        ExecutableQuote {
            if (!Double.isFinite(bid) || !Double.isFinite(ask) || ask < bid)
                throw new IllegalArgumentException("Execution requires a finite, non-crossed bid/ask quote");
        }
        double entryPrice(Order.Side side) { return side.isBuy() ? ask : bid; }
        double exitPrice(boolean longPosition) { return longPosition ? bid : ask; }
        boolean triggersStop(boolean longPosition, double stop) { return longPosition ? bid <= stop : ask >= stop; }
        boolean triggersTakeProfit(boolean longPosition, double takeProfit) { return longPosition ? bid >= takeProfit : ask <= takeProfit; }
    }
    private record BalanceKey(String accountId, String currency) { }

    private static final class InstrumentOrders {
        final double spread;
        final BarExecutionModel prices;
        final List<PendingRequest> inbound = new ArrayList<>();
        final Map<String, AttachedExit> exits = new LinkedHashMap<>();
        final Map<BalanceKey, Order> lastOrders = new LinkedHashMap<>();
        QuoteSnapshot nativeQuote = QuoteSnapshot.EMPTY;
        ExecutableQuote quote;
        BarOpen lastOpen;

        InstrumentOrders(double spread) {
            this.spread = spread;
            this.prices = new BarExecutionModel(spread);
        }
    }
    private static final class AttachedExit {
        final Order parent;
        final double initialStopDistance;
        double stop;
        double takeProfit = Double.NaN;
        PositionTarget.IntrabarExitPolicy intrabarExitPolicy = PositionTarget.IntrabarExitPolicy.STOP_FIRST;
        int barsLeft;
        final boolean persistent;
        AttachedExit(Order parent, double fillPrice) {
            this(parent, fillPrice, parent.getExitRules().stopDistance(), parent.getExitRules().closeAfterBars());
        }
        AttachedExit(Order parent, double fillPrice, double stopDistance) {
            this(parent, fillPrice, stopDistance, 0);
        }
        private AttachedExit(Order parent, double fillPrice, double stopDistance, int barsLeft) {
            this.parent = parent;
            this.initialStopDistance = stopDistance;
            this.stop = fillPrice + (longPosition() ? -1 : 1) * stopDistance;
            this.barsLeft = barsLeft;
            this.persistent = barsLeft == 0;
        }
        boolean longPosition() { return parent.getSide().isBuy(); }
    }
}
