package com.ridgwell.setintersection.keyset;

import com.ridgwell.setintersection.csv.CsvReader;
import com.ridgwell.setintersection.io.RegularFileSource;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Loads a large plain file by splitting it into byte-range chunks ({@link ChunkPlanner})
 * and parsing each chunk on its own virtual thread, merging the resulting per-chunk
 * {@link Counts} back together. Virtual threads make nesting this under the file-level
 * concurrency in {@link TwoFileLoader} cheap - there's no bounded platform-thread pool to
 * exhaust by fanning out twice.
 */
final class ChunkedCsvLoader {

    /** Below this, a chunk isn't worth its own thread and merge overhead. */
    private static final long MIN_CHUNK_BYTES = 4L * 1024 * 1024;

    private ChunkedCsvLoader() {
    }

    /** Reads the header once (if any), plans the chunk ranges, parses them in parallel, and merges the results. */
    static Counts load(RegularFileSource source, Options opts) throws IOException {
        Path path = source.path();
        String label = source.displayPath();
        long fileSize = Files.size(path);

        // The header, if there is one, gets read up front and removed from consideration -
        // everything past dataStart is plain data, so no chunk task has to special-case row 1.
        String[] header = null;
        long dataStart = 0;
        if (opts.hasHeader()) {
            header = readHeaderFields(path, opts.delimiter(), label);
            dataStart = findHeaderEndOffset(path);
        }

        int[] indices = ColumnSelector.resolve(opts.columns(), opts.hasHeader(), header);

        long dataLength = Math.max(0, fileSize - dataStart);
        int available = Runtime.getRuntime().availableProcessors();
        int chunkCount = (int) Math.max(1, Math.min(available, dataLength / MIN_CHUNK_BYTES));

        List<ChunkRange> ranges = ChunkPlanner.plan(path, dataStart, fileSize, chunkCount);

        List<Counts> partials;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Counts>> futures = new ArrayList<>(ranges.size());
            for (ChunkRange range : ranges) {
                futures.add(executor.submit(() -> parseRange(path, range, indices, label, opts.delimiter())));
            }
            partials = new ArrayList<>(futures.size());
            for (Future<Counts> future : futures) {
                partials.add(await(future));
            }
        }

        Counts merged = new Counts(label);
        for (Counts partial : partials) {
            merged.mergeFrom(partial);
        }
        return merged;
    }

    /** Blocks for one chunk's result, unwrapping the checked exception a virtual-thread task's failure gets wrapped in. */
    private static Counts await(Future<Counts> future) throws IOException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while loading chunks", e);
        }
    }

    /** One chunk's work: parse its byte range with an ordinary {@link CsvReader} into a local {@link Counts}. */
    private static Counts parseRange(Path path, ChunkRange range, int[] indices, String label, char delimiter) throws IOException {
        Counts counts = new Counts(label);
        RowCounter rowCounter = new RowCounter(indices, label);
        try (CsvReader reader = new CsvReader(boundedChannelStream(path, range), delimiter, label)) {
            while (reader.nextRecord()) {
                rowCounter.consumeRow(reader, counts);
            }
        }
        return counts;
    }

    /** Opens the file at {@code range.start()} and caps reads at {@code range.end()}, so this chunk can't wander into the next one. */
    private static InputStream boundedChannelStream(Path path, ChunkRange range) throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        channel.position(range.start());
        InputStream base = Channels.newInputStream(channel);
        return new BufferedInputStream(new BoundedInputStream(base, range.end() - range.start()));
    }

    /** Reads just the header row, for resolving column names before any chunk starts parsing. */
    private static String[] readHeaderFields(Path path, char delimiter, String label) throws IOException {
        try (CsvReader reader = new CsvReader(new BufferedInputStream(Files.newInputStream(path)), delimiter, label)) {
            if (!reader.nextRecord()) {
                throw new KeysetException(label + ": expected a header row but the file is empty");
            }
            String[] header = new String[reader.fieldCount()];
            for (int i = 0; i < header.length; i++) {
                header[i] = reader.field(i);
            }
            return header;
        }
    }

    /** A raw byte scan (quote-aware, mirroring {@link ChunkPlanner}) for the offset right after the header's line ending. */
    private static long findHeaderEndOffset(Path path) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            long pos = 0;
            boolean insideQuotes = false;
            int b;
            while ((b = in.read()) != -1) {
                pos++;
                if (b == '"') {
                    insideQuotes = !insideQuotes;
                } else if (b == '\n' && !insideQuotes) {
                    return pos;
                }
            }
            return pos;
        }
    }

    /** Limits a delegate stream to at most {@code limit} more bytes, so a chunk task never reads past its range. */
    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        BoundedInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = delegate.read();
            if (b != -1) {
                remaining--;
            }
            return b;
        }

        // Same idea as read() above, but capping how much of the buffer we ask the
        // delegate to fill so a read spanning the range's end can't pull in the next chunk's bytes.
        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int n = delegate.read(buf, off, toRead);
            if (n > 0) {
                remaining -= n;
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
