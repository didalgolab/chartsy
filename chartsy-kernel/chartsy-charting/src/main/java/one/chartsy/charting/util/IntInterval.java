package one.chartsy.charting.util;

/// Represents an inclusive integer index range, typically emitted by
/// {@link IntIntervalSet#intervalIterator()} when chart updates can be described as a
/// small number of contiguous spans.
///
/// Current charting consumers forward these bounds directly into batching and repaint
/// paths, so both ends identify valid covered indexes rather than an exclusive limit.
public interface IntInterval {
    
    /// Returns the first covered index.
    ///
    /// The returned bound is inclusive.
    int getFirst();
    
    /// Returns the last covered index.
    ///
    /// The returned bound is inclusive, even when the producing collection stores its
    /// upper bound internally in exclusive form.
    int getLast();
}
