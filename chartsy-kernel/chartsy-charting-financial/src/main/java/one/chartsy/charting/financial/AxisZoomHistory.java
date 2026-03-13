package one.chartsy.charting.financial;

import one.chartsy.charting.DataInterval;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.EventListenerList;

/**
 * A simple class that handles zoom history.
 */
public class AxisZoomHistory {
  private int pos = -1;
  private final List<DataInterval> history = new ArrayList<>();
  private EventListenerList historyListeners;

  /**
   * Adds the specified data interval to the <code>AxisZoomHistory</code>.
   */
  public void add(DataInterval itv) {
    if (pos < 0 || pos == history.size() - 1) {
      history.add(itv);
      ++pos;
    } else {
      // Replace an existing element and flush the list.
      history.set(++pos, itv);
      while (history.size() > pos + 1)
        history.remove(pos + 1);
    }
    fireHistoryEvent();
  }

  /**
   * Resets the history.
   */
  public void reset() {
    history.clear();
    pos = -1;
    fireHistoryEvent();
  }

  /**
   * Returns the previous data interval according to the current position in the
   * history. You should call this method after having checked that a previous
   * element is available.
   * 
   * @see #hasPrevious
   */
  public DataInterval previous() {
    DataInterval itv = history.get(--pos);
    fireHistoryEvent();
    return itv;
  }

  /**
   * Returns whether a previous data interval is available.
   */
  public boolean hasPrevious() {
    return (history.size() > 0) && (pos > 0) && (pos < history.size());
  }

  /**
   * Returns the next data interval according to the current position in the
   * history. You should call this method after having checked that a next
   * element is available.
   * 
   * @see #hasNext
   */
  public DataInterval next() {
    DataInterval dw = history.get(++pos);
    fireHistoryEvent();
    return dw;
  }

  /**
   * Returns whether a next data interval is available.
   */
  public boolean hasNext() {
    return (history.size() > 0) && (pos >= 0) && (pos < history.size() - 1);
  }

  /**
   * Adds the specified listener.
   */
  public void addHistoryListener(HistoryListener l) {
    if (historyListeners == null)
      historyListeners = new EventListenerList();
    historyListeners.add(HistoryListener.class, l);
  }

  /**
   * Removes the specified listener.
   */
  public void removeHistoryListener(HistoryListener l) {
    if (historyListeners == null)
      return;
    historyListeners.remove(HistoryListener.class, l);
    if (historyListeners.getListenerList().length == 0)
      historyListeners = null;
  }

  /**
   * Notifies all registered listeners that a change occurred in the history.
   */
  protected void fireHistoryEvent() {
    if (historyListeners == null)
      return;
    Object[] listeners = historyListeners.getListenerList();
    for (int i = listeners.length - 1; i >= 0; i -= 2)
      ((HistoryListener) (listeners[i])).navigationPerformed();
  }
}

