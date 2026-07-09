package com.ridgwell.setintersection.keyset;

import java.io.IOException;

/** Covers every keyset-level failure: unresolved header column, out-of-range column, empty file where a header was expected. */
public final class KeysetException extends IOException {

    public KeysetException(String message) {
        super(message);
    }

    public KeysetException(String message, Throwable cause) {
        super(message, cause);
    }
}
