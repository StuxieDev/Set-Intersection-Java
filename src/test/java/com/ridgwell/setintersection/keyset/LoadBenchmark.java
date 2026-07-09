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
 * <p>Run it with:
 * <pre>
 * mvn test-compile
 * java -cp target/classes;target/test-classes com.ridgwell.setintersection.keyset.LoadBenchmark
 * </pre>
 * (use {@code :} instead of {@code ;} on a non-Windows classpath).
 */
public final class LoadBenchmark {

    private static final int ROW_COUNT = 8_000_000;
    private static final int DISTINCT_KEYS = 2_000_000;
    private static final int WARMUP_RUNS = 2;
    private static final int TIMED_RUNS = 5;

    private LoadBenchmark() {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("set-intersection-bench");
        Path file = dir.resolve("bench.csv");
        try {
            System.out.println("Generating " + ROW_COUNT + " rows (" + DISTINCT_KEYS + " distinct keys)...");
            generate(file, ROW_COUNT, DISTINCT_KEYS);

            long fileSize = Files.size(file);
            System.out.printf("File size: %.1f MiB%n", fileSize / (1024.0 * 1024.0));
            System.out.println("Available processors: " + Runtime.getRuntime().availableProcessors());
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

    @FunctionalInterface
    private interface Loader {
        Counts load() throws IOException;
    }

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
