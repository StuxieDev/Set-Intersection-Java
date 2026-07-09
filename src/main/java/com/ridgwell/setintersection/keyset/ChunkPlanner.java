package com.ridgwell.setintersection.keyset;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits {@code [dataStartOffset, fileSize)} of a file into byte ranges that are safe to
 * parse independently in parallel - each one starts exactly on a real CSV record
 * boundary, never in the middle of a quoted field.
 *
 * <p>You can't find a boundary by just looking at the bytes around a target offset,
 * because whether that offset is inside a quoted field depends on everything read since
 * the last known boundary. So this does one sequential pass starting from
 * {@code dataStartOffset}, which is always a genuine record start (either the very
 * beginning of the data, or the offset right after a header row), tracking a single
 * "am I inside a quoted field right now" flag as it goes.
 *
 * <p>That flag turns out to be easy to track: a quoted field always has an even number of
 * quote characters up to and including its closing quote (one for the open, an even number
 * more for any escaped {@code ""} pairs, one for the close). So just counting quote
 * characters seen so far and checking whether that count is odd or even tells you whether
 * you're inside a quoted field - no lookahead needed to tell "escaped" apart from "closing".
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

    // Read in chunks this size and scan each in memory, rather than one byte at a time
    // through a stream. A first version of this used InputStream.read() per byte, which
    // turned out to matter a lot: on an 87 MiB benchmark file, that single-byte version made
    // the chunked path slower than just parsing serially, because this scan is a whole
    // extra pass over the file stacked on top of the real parse. Reading in 64 KiB batches
    // and scanning the array directly cut the scan down to a small fraction of the total
    // parse time and turned that into a real speedup instead - see LoadBenchmark.
    private static final int SCAN_BUFFER_SIZE = 64 * 1024;

    /** The quote-aware scan described above - one pass, picking up each target boundary as it's crossed. */
    private static long[] findBoundaries(Path path, long dataStartOffset, long fileSize, int chunkCount) throws IOException {
        long dataLength = fileSize - dataStartOffset;
        long[] targets = new long[chunkCount - 1];
        for (int i = 1; i < chunkCount; i++) {
            targets[i - 1] = dataStartOffset + (dataLength * i) / chunkCount;
        }

        long[] boundaries = new long[targets.length];
        int nextTarget = 0;
        boolean insideQuotes = false;

        byte[] buffer = new byte[SCAN_BUFFER_SIZE];
        try (InputStream in = Files.newInputStream(path)) {
            skipFully(in, dataStartOffset);

            long pos = dataStartOffset;
            int read;
            while (nextTarget < targets.length && (read = in.read(buffer)) != -1) {
                for (int i = 0; i < read && nextTarget < targets.length; i++) {
                    byte b = buffer[i];
                    pos++;
                    if (b == '"') {
                        insideQuotes = !insideQuotes;
                    } else if (b == '\n' && !insideQuotes && pos >= targets[nextTarget]) {
                        boundaries[nextTarget++] = pos;
                    }
                }
            }
            while (nextTarget < targets.length) {
                boundaries[nextTarget++] = fileSize;
            }
        }
        return boundaries;
    }

    /** {@code InputStream.skip} is allowed to skip fewer bytes than asked, so this keeps retrying until {@code n} bytes are gone. */
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
