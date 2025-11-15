package one.chartsy.hnsw;

/**
 * Policy that governs how duplicate identifiers are handled during insert.
 */
public enum DuplicatePolicy {
    /** Reject duplicate identifiers and throw an {@link IllegalArgumentException}. */
    REJECT,
    /** Replace the existing vector by performing a remove followed by an add. */
    UPSERT
}
