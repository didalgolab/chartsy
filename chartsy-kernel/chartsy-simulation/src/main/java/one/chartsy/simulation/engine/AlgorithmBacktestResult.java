package one.chartsy.simulation.engine;

import one.chartsy.simulation.reporting.BacktestReport;
import java.util.List;

/** Immutable execution, target-decision and account-ledger evidence at the end of replay. */
public record AlgorithmBacktestResult(BacktestReport report, List<TradingSimulator.Execution> executions,
                                      List<TradingSimulator.EquityPoint> equityCurve, double finalEquity,
                                      List<TradingSimulator.OpenPosition> openPositions,
                                      List<TradingSimulator.TargetDecision> targetDecisions,
                                      double realizedPnl, double unrealizedPnl,
                                      List<TradingSimulator.MaintenanceDecision> maintenanceDecisions,
                                      List<TradingSimulator.CloseDecision> closeDecisions,
                                      List<TradingSimulator.StopUpdateDecision> stopUpdateDecisions) {
    public AlgorithmBacktestResult(BacktestReport report, List<TradingSimulator.Execution> executions,
                                  List<TradingSimulator.EquityPoint> equityCurve, double finalEquity,
                                  List<TradingSimulator.OpenPosition> openPositions,
                                  List<TradingSimulator.TargetDecision> targetDecisions,
                                  double realizedPnl, double unrealizedPnl,
                                  List<TradingSimulator.MaintenanceDecision> maintenanceDecisions,
                                  List<TradingSimulator.CloseDecision> closeDecisions) {
        this(report, executions, equityCurve, finalEquity, openPositions, targetDecisions,
                realizedPnl, unrealizedPnl, maintenanceDecisions, closeDecisions, List.of());
    }
    public AlgorithmBacktestResult(BacktestReport report, List<TradingSimulator.Execution> executions,
                                  List<TradingSimulator.EquityPoint> equityCurve, double finalEquity,
                                  List<TradingSimulator.OpenPosition> openPositions,
                                  List<TradingSimulator.TargetDecision> targetDecisions,
                                  double realizedPnl, double unrealizedPnl,
                                  List<TradingSimulator.MaintenanceDecision> maintenanceDecisions) {
        this(report, executions, equityCurve, finalEquity, openPositions, targetDecisions,
                realizedPnl, unrealizedPnl, maintenanceDecisions, List.of());
    }
    public AlgorithmBacktestResult(BacktestReport report, List<TradingSimulator.Execution> executions,
                                  List<TradingSimulator.EquityPoint> equityCurve, double finalEquity,
                                  List<TradingSimulator.OpenPosition> openPositions,
                                  List<TradingSimulator.TargetDecision> targetDecisions,
                                  double realizedPnl, double unrealizedPnl) {
        this(report, executions, equityCurve, finalEquity, openPositions, targetDecisions, realizedPnl, unrealizedPnl, List.of());
    }
    public AlgorithmBacktestResult(BacktestReport report, List<TradingSimulator.Execution> executions,
                                  List<TradingSimulator.EquityPoint> equityCurve, double finalEquity) {
        this(report, executions, equityCurve, finalEquity, List.of(), List.of(), Double.NaN, Double.NaN);
    }
    public AlgorithmBacktestResult {
        executions = List.copyOf(executions);
        equityCurve = List.copyOf(equityCurve);
        openPositions = List.copyOf(openPositions);
        targetDecisions = List.copyOf(targetDecisions);
        maintenanceDecisions = List.copyOf(maintenanceDecisions);
        closeDecisions = List.copyOf(closeDecisions);
        stopUpdateDecisions = List.copyOf(stopUpdateDecisions);
    }
}
