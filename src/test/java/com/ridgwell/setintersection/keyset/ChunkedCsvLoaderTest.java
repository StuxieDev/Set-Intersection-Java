package com.ridgwell.setintersection.keyset;

import com.ridgwell.setintersection.io.RegularFileSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The main thing that matters for chunking: parse the same file chunked and serial, and
 * make sure they agree. Calls {@link ChunkedCsvLoader#load} directly so the test files
 * only need to clear the loader's internal {@code MIN_CHUNK_BYTES} (a few MB), not
 * {@code KeysetLoader}'s real 32 MiB auto-chunking threshold.
 */
class ChunkedCsvLoaderTest {

    @Test
    void chunkedAndSerialParsesProduceIdenticalCounts(@TempDir Path dir) throws IOException {
        assumeTrue(Runtime.getRuntime().availableProcessors() > 1, "chunking needs more than one core");

        Path path = dir.resolve("large.csv");
        writePaddedCsv(path, false, 60_000, 500);

        RegularFileSource source = new RegularFileSource(path);
        Options opts = Options.singleColumn(false, "0", ',');

        Counts serial = KeysetLoader.loadSerially(source, opts);
        Counts chunked = ChunkedCsvLoader.load(source, opts);

        assertCountsEqual(serial, chunked);
    }

    @Test
    void chunkedParseWithHeaderAndCompositeColumns(@TempDir Path dir) throws IOException {
        assumeTrue(Runtime.getRuntime().availableProcessors() > 1, "chunking needs more than one core");

        Path path = dir.resolve("large-header.csv");
        writePaddedCsv(path, true, 60_000, 500);

        RegularFileSource source = new RegularFileSource(path);
        Options opts = new Options(true, List.of("a", "b"), ',');

        Counts serial = KeysetLoader.loadSerially(source, opts);
        Counts chunked = ChunkedCsvLoader.load(source, opts);

        assertCountsEqual(serial, chunked);
    }

    @Test
    void quotedFieldStraddlingAChunkBoundaryIsStillParsedAsOneKey(@TempDir Path dir) throws IOException {
        assumeTrue(Runtime.getRuntime().availableProcessors() > 1, "chunking needs more than one core");

        Path path = dir.resolve("large-quoted.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (int i = 0; i < 60_000; i++) {
                writer.write("padding-key-" + (i % 500) + "," + "x".repeat(200) + "\n");
            }
            writer.write("\"multi\nline\nquoted\nvalue\",z\n");
        }

        RegularFileSource source = new RegularFileSource(path);
        Options opts = Options.singleColumn(false, "0", ',');

        Counts serial = KeysetLoader.loadSerially(source, opts);
        Counts chunked = ChunkedCsvLoader.load(source, opts);

        assertCountsEqual(serial, chunked);
        assertEquals(1, chunked.frequencyOf("multi\nline\nquoted\nvalue"));
    }

    @Test
    void quotedHeaderNamesAreHandledWhenFindingWhereTheHeaderEnds(@TempDir Path dir) throws IOException {
        assumeTrue(Runtime.getRuntime().availableProcessors() > 1, "chunking needs more than one core");

        Path path = dir.resolve("quoted-header.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("\"a\",\"b\"\n");
            Random random = new Random(11);
            for (int i = 0; i < 60_000; i++) {
                writer.write("part-" + random.nextInt(500) + ",part2-" + random.nextInt(37) + "\n");
            }
        }

        RegularFileSource source = new RegularFileSource(path);
        Options opts = new Options(true, List.of("a"), ',');

        Counts serial = KeysetLoader.loadSerially(source, opts);
        Counts chunked = ChunkedCsvLoader.load(source, opts);

        assertCountsEqual(serial, chunked);
    }

    @Test
    void headerWithNoTrailingNewlineAndNoDataRowsIsAnEmptyResult(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("header-only.csv");
        Files.writeString(path, "a,b");

        RegularFileSource source = new RegularFileSource(path);
        Options opts = new Options(true, List.of("a"), ',');

        Counts counts = ChunkedCsvLoader.load(source, opts);

        assertEquals(0, counts.total());
        assertEquals(0, counts.distinct());
    }

    @Test
    void emptyFileWithHeaderExpectedIsAnError(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("empty.csv");
        Files.writeString(path, "");

        RegularFileSource source = new RegularFileSource(path);
        Options opts = new Options(true, List.of("a"), ',');

        assertThrows(KeysetException.class, () -> ChunkedCsvLoader.load(source, opts));
    }

    private static void writePaddedCsv(Path path, boolean withHeader, int rowCount, int distinctKeys) throws IOException {
        Random random = new Random(7);
        String padding = "x".repeat(200);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            if (withHeader) {
                writer.write("a,b,c\n");
            }
            for (int i = 0; i < rowCount; i++) {
                int k = random.nextInt(distinctKeys);
                writer.write("part-" + k + ",part2-" + (k % 37) + "," + padding + "\n");
            }
        }
    }

    private static void assertCountsEqual(Counts expected, Counts actual) {
        assertEquals(expected.total(), actual.total(), "total");
        assertEquals(expected.distinct(), actual.distinct(), "distinct");
        expected.forEachKey((key, count) -> assertEquals(count, actual.frequencyOf(key), "frequency of " + key));
    }
}
