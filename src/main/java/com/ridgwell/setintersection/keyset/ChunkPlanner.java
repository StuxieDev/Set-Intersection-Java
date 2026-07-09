package com.ridgwell.setintersection.keyset;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits {@code [dataStartOffset, fileSize)} of a file into byte ranges that are safe to
 * parse independently in parallel - each range starts exactly on a real CSV record
 * boundary, never in the middle of a quoted field.
 *
 * <p>A boundary can't be found by looking only at the bytes immediately around a target
 * offset: whether that offset sits inside a quoted field depends on everything read since
 * the last known record boundary. So this makes one sequential pass from
 * {@code dataStartOffset} - a position already known to be a genuine record start, since
 * it's either the very start of the data or the offset right after a header row read by
 * {@link ChunkedCsvLoader} - tracking a single "inside a quoted field" flag.
 *
 * <p>That flag has a pleasant invariant for well-formed CSV: a quoted field always
 * contains an even number of quote characters before its closing quote (the opening quote,
 * plus zero or more escaped {@code ""} pairs), so simply counting quote characters seen so
 * far and testing the count's parity exactly tracks "inside a quoted field" - no need to
 * look ahead for a doubled quote to distinguish "escaped" from "closing".
 */
final class ChunkPlanner {

    private ChunkPlanner() {
    }

    /**
     * Returns up to {@code targetChunkCount} ranges covering {@code [dataStartOffset, fileSize)}.
     * If a target offset falls inside a very large quoted field, the scan simply continues
     * past it and two intended chunks collapse into one - chunk count is a best-effort
     * target, not a guarantee, and this is safe degradation, not corruption.
     */
    static List<ChunkRange> plan(Path path, long dataStartOffset, long fileSize, int targetChunkCount) throws IOException {
        if (targetChunkCount <= 1 || dataStartOffset >= fileSize) {
            return List.of(new ChunkRange(dataStartOffset, fileSize));
        }

        long[] boundaries = findBoundaries(path, dataStartOffset, fileSize, targetChunkCount);

        List<ChunkRange> ranges = new ArrayList<>();
        long start = dataStartOffset;
        for (long boundary : boundaries) {
            if (boundary > start) {
                ranges.add(new ChunkRange(start, boundary));
                start = boundary;
            }
        }
        if (start < fileSize) {
            ranges.add(new ChunkRange(start, fileSize));
        }
        return ranges;
    }

    private static long[] findBoundaries(Path path, long dataStartOffset, long fileSize, int chunkCount) throws IOException {
        long dataLength = fileSize - dataStartOffset;
        long[] targets = new long[chunkCount - 1];
        for (int i = 1; i < chunkCount; i++) {
            targets[i - 1] = dataStartOffset + (dataLength * i) / chunkCount;
        }

        long[] boundaries = new long[targets.length];
        int nextTarget = 0;
        boolean insideQuotes = false;

        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            skipFully(in, dataStartOffset);

            long pos = dataStartOffset;
            int b;
            while (nextTarget < targets.length && (b = in.read()) != -1) {
                pos++;
                if (b == '"') {
                    insideQuotes = !insideQuotes;
                } else if (b == '\n' && !insideQuotes && pos >= targets[nextTarget]) {
                    boundaries[nextTarget++] = pos;
                }
            }
            while (nextTarget < targets.length) {
                boundaries[nextTarget++] = fileSize;
            }
        }
        return boundaries;
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        while (n > 0) {
            long skipped = in.skip(n);
            if (skipped <= 0) {
                if (in.read() == -1) {
                    return;
                }
                skipped = 1;
            }
            n -= skipped;
        }
    }
}
