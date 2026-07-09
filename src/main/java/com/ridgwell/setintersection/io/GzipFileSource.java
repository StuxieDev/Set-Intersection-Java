package com.ridgwell.setintersection.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * A gzip-compressed on-disk file. Never chunkable: a byte offset into the compressed
 * stream has no correspondence to a record boundary in the decompressed content, so this
 * source is always read as one serial stream regardless of its (compressed) file size.
 */
public record GzipFileSource(Path path) implements InputSource {

    /** Opens the file and wraps it in a {@link GZIPInputStream} so callers see decompressed bytes. */
    @Override
    public InputStream openStream() throws IOException {
        return new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path)));
    }

    /** The compressed file's own path, as given on the command line. */
    @Override
    public String displayPath() {
        return path.toString();
    }
}
