package one.chartsy.charting.financial;

/**
 * An interface to handle zoom history.
 */
public interface HistoryListener extends java.util.EventListener {
  /**
   * Called when a history event occurs.
   */
  void navigationPerformed();
}

