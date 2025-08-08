package one.chartsy.data.signal;

import java.util.Optional;

/**
 * Defines a generic detector for trade signals based on provided data type.
 *
 * @param <T> the type of data used to detect trade signals
 */
public interface TradeSignalDetector<T> {

    /**
     * Detects a trade signal based on provided data.
     *
     * @param data the data used for detecting the trade signal
     * @return an {@link Optional} containing the trade signal if detected, otherwise empty
     */
    Optional<TradeSignal> detect(T data);
}