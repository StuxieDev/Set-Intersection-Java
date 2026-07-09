package com.ridgwell.setintersection.keyset;

import java.util.List;

/**
 * Controls how one CSV file is parsed into keys.
 *
 * @param hasHeader whether the first row is a header rather than data
 * @param columns   the key column specs, in order - each is either a zero-based numeric
 *                  index or, when {@code hasHeader} is true, a header name. A single
 *                  entry is an ordinary single-column key; more than one entry makes a
 *                  composite key (see {@link CompositeKeyEncoder}).
 * @param delimiter the field separator; must be a single ASCII character
 */
public record Options(boolean hasHeader, List<String> columns, char delimiter) {

    // Compact constructor: validates columns and makes a defensive immutable copy, since
    // this is called from the CLI with a caller-owned mutable List.
    public Options {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }
        columns = List.copyOf(columns);
    }

    /** Convenience for the common case of one key column, so callers don't have to wrap it in a List themselves. */
    public static Options singleColumn(boolean hasHeader, String column, char delimiter) {
        return new Options(hasHeader, List.of(column), delimiter);
    }
}
