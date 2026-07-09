package com.ridgwell.setintersection.keyset;

import com.ridgwell.setintersection.io.InputSource;
import com.ridgwell.setintersection.io.InputSourceFactory;
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

/** Direct ports of keyset_test.go's TestLoad_* cases. */
class KeysetLoaderTest {

    private static Path writeCsv(Path dir, String name, List<String> lines) throws IOException {
        Path path = dir.resolve(name);
        Files.writeString(path, String.join("\n", lines) + "\n");
        return path;
    }

    private static Counts load(Path path, Options opts) throws IOException {
        InputSource source = InputSourceFactory.resolve(path.toString());
        return KeysetLoader.load(source, opts);
    }

    @Test
    void noHeaderCountsEveryRowAsAKey(@TempDir Path dir) throws IOException {
        Path path = writeCsv(dir, "data.csv", List.of("A", "B", "C", "D", "D", "E", "F", "F"));

        Counts c = load(path, Options.singleColumn(false, "0", ','));

        assertEquals(8, c.total());
        assertEquals(6, c.distinct());
        assertEquals(2, c.frequencyOf("D"));
        assertEquals(2, c.frequencyOf("F"));
    }

    @Test
    void headerByNameResolvesTheColumn(@TempDir Path dir) throws IOException {
        Path path = writeCsv(dir, "data.csv", List.of(
                "id,udprn,name",
                "1,30433784,Alice",
                "2,08034283,Bob",
                "3,30433784,Carol"));

        Counts c = load(path, Options.singleColumn(true, "udprn", ','));

        assertEquals(3, c.total());
        assertEquals(2, c.distinct());
        assertEquals(2, c.frequencyOf("30433784"));
    }

    @Test
    void headerByIndexPreservesLeadingZeros(@TempDir Path dir) throws IOException {
        Path path = writeCsv(dir, "data.csv", List.of(
                "id,udprn",
                "1,00012345",
                "2,00067890"));

        Counts c = load(path, Options.singleColumn(true, "1", ','));

        assertEquals(2, c.distinct());
        assertEquals(1, c.frequencyOf("00012345"));
    }

    @Test
    void unknownHeaderColumnNameIsAnError(@TempDir Path dir) throws IOException {
        Path path = writeCsv(dir, "data.csv", List.of("id,udprn", "1,00012345"));

        assertThrows(IOException.class,
                () -> load(path, Options.singleColumn(true, "does-not-exist", ',')));
    }

    @Test
    void outOfRangeColumnIndexIsAnError(@TempDir Path dir) throws IOException {
        Path path = writeCsv(dir, "data.csv", List.of("A", "B"));

        assertThrows(IOException.class,
                () -> load(path, Options.singleColumn(false, "5", ',')));
    }

    @Test
    void missingFileIsAnError(@TempDir Path dir) {
        Path missing = dir.resolve("nope.csv");

        assertThrows(IOException.class,
                () -> load(missing, Options.singleColumn(false, "0", ',')));
    }

    @Test
    void loadSeriallyErrorsOnAnEmptyFileWhenAHeaderIsExpected(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("empty.csv");
        Files.writeString(path, "");
        InputSource source = InputSourceFactory.resolve(path.toString());

        assertThrows(KeysetException.class,
                () -> KeysetLoader.loadSerially(source, Options.singleColumn(true, "0", ',')));
    }

    /**
     * Every other chunked-vs-serial test in this project calls {@link ChunkedCsvLoader#load}
     * directly, which proves the chunking logic itself is correct but never actually proves
     * that {@link KeysetLoader#load} - the real public entry point - routes a large file
     * there in the first place. This generates a file past {@link KeysetLoader#CHUNK_THRESHOLD_BYTES}
     * and goes through {@code load}, not {@code ChunkedCsvLoader.load}, specifically to
     * exercise that dispatch decision.
     */
    @Test
    void loadRoutesAFileOverTheChunkThresholdThroughThePublicEntryPoint(@TempDir Path dir) throws IOException {
        assumeTrue(Runtime.getRuntime().availableProcessors() > 1, "chunking needs more than one core");

        Path path = dir.resolve("large.csv");
        writePastChunkThreshold(path);

        InputSource source = InputSourceFactory.resolve(path.toString());
        Options opts = Options.singleColumn(false, "0", ',');

        Counts viaPublicEntryPoint = KeysetLoader.load(source, opts);
        Counts knownSerial = KeysetLoader.loadSerially(source, opts);

        assertEquals(knownSerial.total(), viaPublicEntryPoint.total());
        assertEquals(knownSerial.distinct(), viaPublicEntryPoint.distinct());
        knownSerial.forEachKey((key, count) ->
                assertEquals(count, viaPublicEntryPoint.frequencyOf(key), "frequency of " + key));
    }

    private static void writePastChunkThreshold(Path path) throws IOException {
        Random random = new Random(3);
        String padding = "x".repeat(200);
        long targetSize = KeysetLoader.CHUNK_THRESHOLD_BYTES + 4L * 1024 * 1024;
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            long written = 0;
            while (written < targetSize) {
                String line = "part-" + random.nextInt(1000) + "," + padding + "\n";
                writer.write(line);
                written += line.length();
            }
        }
    }
}
