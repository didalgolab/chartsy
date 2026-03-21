package one.chartsy.charting.event;

import java.io.Serializable;
import java.util.Iterator;

import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.util.IntInterval;
import one.chartsy.charting.util.IntIntervalSet;

/// Wraps a `DataSetListener` and coalesces batched contents events before forwarding them.
///
/// Between `BATCH_BEGIN` and `BATCH_END`, the listener accumulates contents changes and emits them
/// only when the batch closes. Two batching strategies are supported:
///
/// - with `coalesceToSingleEvent == true`, the batch is reduced to one merged
///   [DataSetContentsEvent] when the event mix stays representable
/// - with `coalesceToSingleEvent == false`, the listener preserves separate added,
///   data-changed, and label-changed ranges and expands them again at batch end
///
/// If the received event sequence cannot be represented safely in the chosen strategy, the batch
/// degrades to one `FULL_UPDATE` event for the whole dataset.
///
/// Property changes are never buffered and are forwarded immediately. Active batch bookkeeping is
/// transient, so serialization preserves only the delegate and batching mode, not an in-progress
/// batch.
public class BatchingDataSetListener implements DataSetListener, Serializable {
    private final DataSetListener delegate;
    private final boolean coalesceToSingleEvent;
    private transient DataSetContentsEvent batchBeginEvent;
    private transient boolean fullUpdateRequired;
    private transient int mergedType;
    private transient int mergedFirstIndex;
    private transient int mergedLastIndex;
    private transient IntIntervalSet dataChangedIntervals;
    private transient IntIntervalSet labelChangedIntervals;
    private transient int addedFirstIndex;
    private transient int addedLastIndex;

    /// Creates a batching listener that forwards coalesced events to `delegate`.
    ///
    /// @param delegate              listener that receives the coalesced output
    /// @param coalesceToSingleEvent `true` to prefer one merged `DataSetContentsEvent` per batch
    ///                                                                  when possible; `false` to preserve separate added, data-changed, and
    ///                                                                  label-changed ranges
    public BatchingDataSetListener(DataSetListener delegate, boolean coalesceToSingleEvent) {
        this.delegate = delegate;
        this.coalesceToSingleEvent = coalesceToSingleEvent;
        resetBatchState();
    }

    private void accumulateChangedRange(int firstIndex, int lastIndex) {
        if (!coalesceToSingleEvent) {
            if (dataChangedIntervals == null)
                dataChangedIntervals = new IntIntervalSet();
            dataChangedIntervals.add(firstIndex, lastIndex);
            return;
        }
        if (mergedType == 0) {
            mergedType = DataSetContentsEvent.DATA_CHANGED;
            mergedFirstIndex = firstIndex;
            mergedLastIndex = lastIndex;
            return;
        }
        if (mergedType != DataSetContentsEvent.DATA_CHANGED
                && mergedType != DataSetContentsEvent.DATA_LABEL_CHANGED) {
            markFullUpdateRequired();
            return;
        }
        mergedType = DataSetContentsEvent.DATA_CHANGED;
        mergedFirstIndex = Math.min(mergedFirstIndex, firstIndex);
        mergedLastIndex = Math.max(mergedLastIndex, lastIndex);
    }

    private void accumulateDataAddedRange(int firstIndex, int lastIndex) {
        if (!coalesceToSingleEvent) {
            if (!hasAddedRange()) {
                addedFirstIndex = firstIndex;
                addedLastIndex = lastIndex;
                return;
            }
            if (firstIndex == addedLastIndex + 1) {
                addedLastIndex = lastIndex;
                return;
            }
            throw new IllegalStateException("inconsistent DataSetContentsEvent.DATA_ADDED events received");
        }
        if (mergedType == 0) {
            mergedType = DataSetContentsEvent.DATA_ADDED;
            mergedFirstIndex = firstIndex;
            mergedLastIndex = lastIndex;
            return;
        }
        if (mergedType == DataSetContentsEvent.DATA_ADDED && firstIndex == mergedLastIndex + 1) {
            mergedLastIndex = lastIndex;
            return;
        }
        markFullUpdateRequired();
    }

    private void accumulateLabelChangedRange(int firstIndex, int lastIndex) {
        if (!coalesceToSingleEvent) {
            if (labelChangedIntervals == null)
                labelChangedIntervals = new IntIntervalSet();
            labelChangedIntervals.add(firstIndex, lastIndex);
            return;
        }
        if (mergedType == 0) {
            mergedType = DataSetContentsEvent.DATA_LABEL_CHANGED;
            mergedFirstIndex = firstIndex;
            mergedLastIndex = lastIndex;
            return;
        }
        if (mergedType != DataSetContentsEvent.DATA_LABEL_CHANGED
                && mergedType != DataSetContentsEvent.DATA_CHANGED) {
            markFullUpdateRequired();
            return;
        }
        mergedFirstIndex = Math.min(mergedFirstIndex, firstIndex);
        mergedLastIndex = Math.max(mergedLastIndex, lastIndex);
    }

    private boolean hasAddedRange() {
        return addedFirstIndex <= addedLastIndex;
    }

    private boolean isBatching() {
        return batchBeginEvent != null;
    }

    private boolean isNonEmptyRange(DataSetContentsEvent event) {
        return event.getFirstIdx() <= event.getLastIdx();
    }

    private void markFullUpdateRequired() {
        fullUpdateRequired = true;
        mergedType = 0;
        dataChangedIntervals = null;
        labelChangedIntervals = null;
        addedFirstIndex = 0;
        addedLastIndex = -1;
    }

    private void emitExpandedBatch(DataSetContentsEvent beginEvent, DataSet dataSet,
                                   IntIntervalSet dataChangedIntervals, IntIntervalSet labelChangedIntervals,
                                   int addedFirstIndex, int addedLastIndex) {
        boolean hasAddedRange = addedFirstIndex <= addedLastIndex;
        if (hasAddedRange) {
            if (dataChangedIntervals != null)
                dataChangedIntervals.remove(addedFirstIndex, addedLastIndex);
            if (labelChangedIntervals != null)
                labelChangedIntervals.remove(addedFirstIndex, addedLastIndex);
        }
        if (dataChangedIntervals != null && labelChangedIntervals != null) {
            Iterator<IntInterval> intervalIterator = dataChangedIntervals.intervalIterator();
            while (intervalIterator.hasNext()) {
                IntInterval interval = intervalIterator.next();
                labelChangedIntervals.remove(interval.getFirst(), interval.getLast());
            }
        }

        int emittedEventCount = countIntervals(dataChangedIntervals)
                + countIntervals(labelChangedIntervals)
                + (hasAddedRange ? 1 : 0);
        if (emittedEventCount > 1)
            delegate.dataSetContentsChanged(beginEvent);

        emitIntervalEvents(dataSet, DataSetContentsEvent.DATA_CHANGED, dataChangedIntervals);
        emitIntervalEvents(dataSet, DataSetContentsEvent.DATA_LABEL_CHANGED, labelChangedIntervals);
        if (hasAddedRange)
            delegate.dataSetContentsChanged(new DataSetContentsEvent(dataSet, DataSetContentsEvent.DATA_ADDED,
                    addedFirstIndex, addedLastIndex));

        if (emittedEventCount > 1)
            delegate.dataSetContentsChanged(new DataSetContentsEvent(dataSet, DataSetContentsEvent.BATCH_END, -1, -1));
    }

    private void emitIntervalEvents(DataSet dataSet, int eventType, IntIntervalSet intervals) {
        if (intervals == null)
            return;
        Iterator<IntInterval> intervalIterator = intervals.intervalIterator();
        while (intervalIterator.hasNext()) {
            IntInterval interval = intervalIterator.next();
            delegate.dataSetContentsChanged(new DataSetContentsEvent(dataSet, eventType, interval.getFirst(),
                    interval.getLast()));
        }
    }

    private void finishBatch() {
        if (!isBatching())
            return;

        DataSetContentsEvent beginEvent = batchBeginEvent;
        DataSet dataSet = beginEvent.getDataSet();
        boolean emitFullUpdate = fullUpdateRequired;
        int mergedType = this.mergedType;
        int mergedFirstIndex = this.mergedFirstIndex;
        int mergedLastIndex = this.mergedLastIndex;
        IntIntervalSet dataChangedIntervals = this.dataChangedIntervals;
        IntIntervalSet labelChangedIntervals = this.labelChangedIntervals;
        int addedFirstIndex = this.addedFirstIndex;
        int addedLastIndex = this.addedLastIndex;
        clearBatchState();

        if (emitFullUpdate) {
            delegate.dataSetContentsChanged(new DataSetContentsEvent(dataSet));
            return;
        }
        if (coalesceToSingleEvent) {
            if (mergedType != 0)
                delegate.dataSetContentsChanged(new DataSetContentsEvent(dataSet, mergedType, mergedFirstIndex,
                        mergedLastIndex));
            return;
        }
        emitExpandedBatch(beginEvent, dataSet, dataChangedIntervals, labelChangedIntervals, addedFirstIndex,
                addedLastIndex);
    }

    private int countIntervals(IntIntervalSet intervals) {
        return (intervals == null) ? 0 : intervals.size();
    }

    private void clearBatchState() {
        batchBeginEvent = null;
        resetBatchState();
    }

    private void resetBatchState() {
        fullUpdateRequired = false;
        mergedType = 0;
        mergedFirstIndex = 0;
        mergedLastIndex = 0;
        dataChangedIntervals = null;
        labelChangedIntervals = null;
        addedFirstIndex = 0;
        addedLastIndex = -1;
    }

    private void startBatch(DataSetContentsEvent event) {
        if (isBatching())
            return;
        resetBatchState();
        batchBeginEvent = event;
    }

    /// Consumes one dataset contents event, buffering it when a batch is active.
    ///
    /// Outside a batch, every event except unmatched batch markers is forwarded immediately.
    /// Inside a batch:
    ///
    /// - `AFTER_DATA_CHANGED` is treated the same as `DATA_CHANGED`
    /// - `BEFORE_DATA_CHANGED` is dropped because it has no stable post-batch meaning
    /// - non-coalescing mode keeps separate interval sets and contiguous added ranges
    /// - coalescing mode tries to collapse the batch into one merged event
    ///
    /// If a received sequence cannot be represented safely, the listener switches to `FULL_UPDATE`
    /// for the remainder of that batch.
    @Override
    public void dataSetContentsChanged(DataSetContentsEvent event) {
        switch (event.getType()) {
            case DataSetContentsEvent.BATCH_BEGIN:
                startBatch(event);
                break;

            case DataSetContentsEvent.BATCH_END:
                finishBatch();
                break;

            case DataSetContentsEvent.BEFORE_DATA_CHANGED:
                if (!isBatching())
                    delegate.dataSetContentsChanged(event);
                break;

            case DataSetContentsEvent.AFTER_DATA_CHANGED:
            case DataSetContentsEvent.DATA_CHANGED:
                if (!isBatching()) {
                    delegate.dataSetContentsChanged(event);
                    break;
                }
                if (fullUpdateRequired || !isNonEmptyRange(event))
                    break;
                accumulateChangedRange(event.getFirstIdx(), event.getLastIdx());
                break;

            case DataSetContentsEvent.DATA_ADDED:
                if (!isBatching()) {
                    delegate.dataSetContentsChanged(event);
                    break;
                }
                if (fullUpdateRequired || !isNonEmptyRange(event))
                    break;
                accumulateDataAddedRange(event.getFirstIdx(), event.getLastIdx());
                break;

            case DataSetContentsEvent.DATA_LABEL_CHANGED:
                if (!isBatching()) {
                    delegate.dataSetContentsChanged(event);
                    break;
                }
                if (fullUpdateRequired || !isNonEmptyRange(event))
                    break;
                accumulateLabelChangedRange(event.getFirstIdx(), event.getLastIdx());
                break;

            case DataSetContentsEvent.FULL_UPDATE:
                if (!isBatching()) {
                    delegate.dataSetContentsChanged(event);
                    break;
                }
                markFullUpdateRequired();
                break;

            default:
                break;
        }
    }

    /// Forwards dataset property changes immediately without batching.
    @Override
    public void dataSetPropertyChanged(DataSetPropertyEvent event) {
        delegate.dataSetPropertyChanged(event);
    }
}
