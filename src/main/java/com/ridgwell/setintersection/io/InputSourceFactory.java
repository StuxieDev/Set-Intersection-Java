package com.ridgwell.setintersection.io;

import java.nio.file.Path;
import java.util.Locale;

/** Resolves a raw CLI path argument into the right kind of {@link InputSource}. */
public final class InputSourceFactory {

    private InputSourceFactory() {
    }

    /**
     * {@code "-"} means standard input; a {@code .gz}/{@code .gzip} suffix (case
     * insensitive) means a gzip-compressed file; anything else is a plain file.
     */
    public static InputSource resolve(String rawArg) {
        if (rawArg.equals("-")) {
            return new StdinSource();
        }
        String lower = rawArg.toLowerCase(Locale.ROOT);
        Path path = Path.of(rawArg);
        if (lower.endsWith(".gz") || lower.endsWith(".gzip")) {
            return new GzipFileSource(path);
        }
        return new RegularFileSource(path);
    }
}
