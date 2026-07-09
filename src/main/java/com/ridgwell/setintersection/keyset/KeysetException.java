package com.ridgwell.setintersection.keyset;

import java.io.IOException;

/**
 * Covers every keyset-level failure: unresolved header column, out-of-range column, empty
 * file where a header was expected.
 *
 * <p>A {@code csv.CsvParseException} is a sibling of this, not a subtype - see its own
 * Javadoc for why. Both are plain {@code IOException}s, so a caller wanting to treat "the
 * CSV was malformed" and "the columns didn't resolve" the same way already can.
 */
public final class KeysetException extends IOException {

    private static final long serialVersionUID = 1L;

    /** For a failure with nothing lower-level to wrap - the message alone explains it (e.g. an unresolved header column). */
    public KeysetException(String message) {
        super(message);
    }

    /** For wrapping a lower-level failure (e.g. the file couldn't be opened) with more context. */
    public KeysetException(String message, Throwable cause) {
        super(message, cause);
    }
}
