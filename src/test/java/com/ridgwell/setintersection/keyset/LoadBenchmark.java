package com.ridgwell.setintersection.keyset;

import com.ridgwell.setintersection.io.RegularFileSource;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Not a JUnit test (no {@code @Test} methods, so surefire skips it) - a small standalone
 * tool for actually measuring whether {@link ChunkedCsvLoader} is worth having, instead of
 * just asserting that it is. No JMH here either, same "no frameworks" reasoning as the rest
 * of the project - just {@code System.nanoTime()} around a few warmed-up runs, which is
 * good enough to tell a real effect apart from noise at this scale.
 *
 * <p>Runs two scenarios with the same row count but very different key cardinality, because
 * that turned out to matter: {@link ChunkedCsvLoader} merges every chunk's map back together
 * on a single thread at the end, and that merge cost scales with how many distinct keys each
 * chunk saw. A high-cardinality file (most rows distinct) pays a much bigger merge cost than
 * a low-cardinality one (most rows repeats), even at the same row count.
 *
 * <p>Run it with:
 * <pre>
 * mvn test-compile
 * java -cp target/classes;target/test-classes com.ridgwell.setintersection.keyset.LoadBenchmark
 * </pre>
 * (use {@code :} instead of {@code ;} on a non-Windows classpath).
 */
public final class LoadBenchmark {

    private static final int ROW_COUNT = 8_000_000;
    private static final int WARMUP_RUNS = 2;
    private static final int TIMED_RUNS = 5;

    private LoadBenchmark() {
    }

    /** Runs the high- then low-cardinality scenarios back to back and prints both reports. */
    public static void main(String[] args) throws IOException {
        System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println();

        runScenario("High cardinality", ROW_COUNT, 2_000_000);
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println();
        runScenario("Low cardinality", ROW_COUNT, 1_000);
    }

    /** Generates one synthetic file, then times serial vs. chunked loading on it and prints the comparison. */
    private static void runScenario(String label, int rowCount, int distinctKeys) throws IOException {
        System.out.println(label + " (" + rowCount + " rows, " + distinctKeys + " distinct keys)");

        Path dir = Files.createTempDirectory("set-intersection-bench");
        Path file = dir.resolve("bench.csv");
        try {
            generate(file, rowCount, distinctKeys);

            long fileSize = Files.size(file);
            System.out.printf("File size: %.1f MiB%n", fileSize / (1024.0 * 1024.0));
            System.out.println();

            RegularFileSource source = new RegularFileSource(file);
            Options opts = Options.singleColumn(false, "0", ',');

            System.out.println("Warming up the JIT (results discarded)...");
            for (int i = 0; i < WARMUP_RUNS; i++) {
                KeysetLoader.loadSerially(source, opts);
                ChunkedCsvLoader.load(source, opts);
            }
            System.out.println();

            double serialAvg = timeRuns("Serial", () -> KeysetLoader.loadSerially(source, opts));
            System.out.println();
            double chunkedAvg = timeRuns("Chunked", () -> ChunkedCsvLoader.load(source, opts));

            System.out.println();
            System.out.printf("Serial avg:  %.2fs%n", serialAvg);
            System.out.printf("Chunked avg: %.2fs%n", chunkedAvg);
            System.out.printf("Speedup:     %.2fx%n", serialAvg / chunkedAvg);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }

    /** Just something with a {@code load()} method that can throw - lets {@code timeRuns} take either the serial or chunked call as a plain lambda. */
    @FunctionalInterface
    private interface Loader {
        Counts load() throws IOException;
    }

    /** Runs {@code loader} {@link #TIMED_RUNS} times, printing each run and returning the average in seconds. */
    private static double timeRuns(String label, Loader loader) throws IOException {
        System.out.println(label + ":");
        long total = 0;
        for (int i = 0; i < TIMED_RUNS; i++) {
            long start = System.nanoTime();
            Counts counts = loader.load();
            long elapsed = System.nanoTime() - start;
            total += elapsed;
            System.out.printf("  run %d: %.2fs (total=%d, distinct=%d)%n", i + 1, elapsed / 1e9, counts.total(), counts.distinct());
        }
        return total / (double) TIMED_RUNS / 1e9;
    }

    /** Writes {@code rowCount} rows of a single {@code key-<n>} column, {@code n} drawn from {@code [0, distinctKeys)}. */
    private static void generate(Path path, int rowCount, int distinctKeys) throws IOException {
        Random random = new Random(42);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (int i = 0; i < rowCount; i++) {
                writer.write("key-");
                writer.write(Integer.toString(random.nextInt(distinctKeys)));
                writer.write('\n');
            }
        }
    }
}
