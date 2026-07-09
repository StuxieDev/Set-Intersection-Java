package com.ridgwell.setintersection.keyset;

import com.ridgwell.setintersection.csv.CsvReader;
import com.ridgwell.setintersection.io.GzipFileSource;
import com.ridgwell.setintersection.io.InputSource;
import com.ridgwell.setintersection.io.RegularFileSource;
import com.ridgwell.setintersection.io.StdinSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** Reads one CSV source and returns key frequency counts for it - the single public entry point for loading a file. */
public final class KeysetLoader {

    /** Below this size, a serial pass is cheap enough that chunking wouldn't pay for its own overhead. */
    static final long CHUNK_THRESHOLD_BYTES = 32L * 1024 * 1024;

    private KeysetLoader() {
    }

    /**
     * Dispatches to a parallel, chunked load for large plain files, or a single serial
     * pass otherwise. Java 21's pattern matching for switch over the sealed
     * {@link InputSource} makes this dispatch exhaustive at compile time.
     */
    public static Counts load(InputSource source, Options opts) throws IOException {
        return switch (source) {
            case RegularFileSource rfs when isLargeEnoughToChunk(rfs) -> ChunkedCsvLoader.load(rfs, opts);
            case RegularFileSource rfs -> loadSerially(rfs, opts);
            case GzipFileSource gzip -> loadSerially(gzip, opts);
            case StdinSource stdin -> loadSerially(stdin, opts);
        };
    }

    /** Whether this file is worth the overhead of splitting into chunks - only matters for a regular, seekable file. */
    private static boolean isLargeEnoughToChunk(RegularFileSource source) {
        if (Runtime.getRuntime().availableProcessors() <= 1) {
            return false;
        }
        try {
            return Files.size(source.path()) >= CHUNK_THRESHOLD_BYTES;
        } catch (IOException e) {
            // Let the ordinary serial path surface this as a clear open/read failure.
            return false;
        }
    }

    /** One pass, top to bottom - the path taken by anything that isn't a large plain file. */
    static Counts loadSerially(InputSource source, Options opts) throws IOException {
        InputStream raw;
        try {
            raw = source.openStream();
        } catch (IOException e) {
            throw new KeysetException("open " + source.displayPath() + ": " + e.getMessage(), e);
        }

        try (CsvReader reader = new CsvReader(raw, opts.delimiter(), source.displayPath())) {
            String[] header = null;
            if (opts.hasHeader()) {
                if (!reader.nextRecord()) {
                    throw new KeysetException(source.displayPath() + ": expected a header row but the file is empty");
                }
                header = copyFields(reader);
            }

            int[] indices = ColumnSelector.resolve(opts.columns(), opts.hasHeader(), header);
            Counts counts = new Counts(source.displayPath());
            RowCounter rowCounter = new RowCounter(indices, source.displayPath());

            while (reader.nextRecord()) {
                rowCounter.consumeRow(reader, counts);
            }
            return counts;
        }
    }

    /** Snapshots the header row's fields before the reader moves on to data rows. */
    private static String[] copyFields(CsvReader reader) {
        String[] fields = new String[reader.fieldCount()];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = reader.field(i);
        }
        return fields;
    }
}
