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

    private final AtomicBoolean consumed = new AtomicBoolean(false);

    @Override
    public InputStream openStream() {
        if (!consumed.compareAndSet(false, true)) {
            throw new IllegalStateException("stdin can only be opened once per process");
        }
        return System.in;
    }

    @Override
    public String displayPath() {
        return "<stdin>";
    }
}
