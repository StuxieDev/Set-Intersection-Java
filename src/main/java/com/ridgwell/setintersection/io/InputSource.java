package com.ridgwell.setintersection.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * A source of CSV bytes: a regular file, a gzip-compressed file, or standard input.
 *
 * <p>Sealed so {@code KeysetLoader} can exhaustively pattern-match over the three cases
 * (Java 21 pattern matching for switch) instead of relying on a boolean flag - the
 * compiler guarantees every case is handled, and adding a fourth kind of source would be
 * a compile error at every switch until handled.
 */
public sealed interface InputSource permits RegularFileSource, GzipFileSource, StdinSource {

    /** Opens a fresh byte stream over this source's content. */
    InputStream openStream() throws IOException;

    /** A human-readable label for error messages and report output. */
    String displayPath();
}
