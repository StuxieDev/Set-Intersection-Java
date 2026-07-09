package com.ridgwell.setintersection.io;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standard input, selected with {@code -}. Never chunkable (can't be seeked or read
 * twice) and can only be opened once per process - opening it a second time (e.g. if both
 * {@code -file1} and {@code -file2} were "-") is a programming error the caller must
 * prevent before this point, so this throws rather than silently returning an exhausted
 * stream.
 */
public final class StdinSource implements InputSource {

    // Tracks whether this instance's stream has already been handed out. Guards against
    // opening System.in twice (which would silently hand back an already-exhausted stream).
    private final AtomicBoolean consumed = new AtomicBoolean(false);

    /** Returns {@code System.in} the first time it's called; throws on every call after that. */
    @Override
    public InputStream openStream() {
        if (!consumed.compareAndSet(false, true)) {
            throw new IllegalStateException("stdin can only be opened once per process");
        }
        return System.in;
    }

    /** A fixed placeholder label, since stdin has no path of its own. */
    @Override
    public String displayPath() {
        return "<stdin>";
    }
}
