package com.ridgwell.setintersection.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A plain, uncompressed on-disk file. The only source kind that can be split into
 * byte-range chunks and parsed in parallel (see {@code keyset.ChunkedCsvLoader}), because
 * its on-disk byte offsets are exactly its content offsets.
 */
public record RegularFileSource(Path path) implements InputSource {

    @Override
    public InputStream openStream() throws IOException {
        return new BufferedInputStream(Files.newInputStream(path));
    }

    @Override
    public String displayPath() {
        return path.toString();
    }
}
